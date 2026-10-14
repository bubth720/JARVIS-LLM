package fr.jarvis.llm

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.jarvis.domain.*
import fr.jarvis.domain.resources.Module
import fr.jarvis.llm.diagnostic.DiagnosticJson
import fr.jarvis.domain.catalogue.*
import fr.jarvis.llm.catalogue.CatalogueStore
import fr.jarvis.llm.engine.LlamaBackend
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicBoolean

data class ChatState(val messages: List<Message> = emptyList(), val status: String = "Catalogue local disponible ; GGUF requis seulement pour les demandes non couvertes.",
                     val busy: Boolean = false, val profile: Profile = Profile.AUTO, val models: Set<ModelKind> = emptySet(),
                     val diagnostic: String = "", val capturing: Boolean = false)

class ChatViewModel @JvmOverloads constructor(app: Application, initialBackend: LanguageModel? = null) : AndroidViewModel(app) {
    private val state = MutableStateFlow(ChatState())
    val ui = state.asStateFlow()
    internal var periodicObservationCount = 0
        private set
    private val resourceOwner = "viewmodel-" + java.util.UUID.randomUUID().toString()
    private val wake = MutableStateFlow(0L)
    private val capture = DiagnosticCapture()
    private var startedWithResident = false
    private var modelsAtCaptureStart: Map<ModelKind, Pair<String?, Long>> = emptyMap()
    private var lastDiagnosticUpdate = 0L
    private val policy = SafetyPolicy()
    private val store = ModelStore(app)
    private val catalogue = CatalogueStore(app)
    private val router = IntentRouter(catalogue)
    private var clarifications: List<String> = emptyList()
    @Volatile private var catalogueDiagnostic = "Catalogue local : initialisation différée ; embeddings désactivés"
    private var backend: LanguageModel? = initialBackend
    private var workBusy = false
    private var pendingReleases = 0
    private val stopped = AtomicBoolean(false)
    private var resident: RuntimeConfig? = null
    private var active: RuntimeConfig? = null
    private var baselineModel: ModelKind? = null
    private var residentBaselinePss: Long? = null
    private var lastUse = 0L
    private var lastDiagnostic = 0L
    private var stoppingReason: String? = null
    private val monitor = DeviceMonitor(app) { enforceSafety() }
    init {
        update { it.copy(models = store.available(), diagnostic = monitor.diagnostic()) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { catalogue.stats() } }.onSuccess { catalogueDiagnostic = it }
            update { it.copy(diagnostic = diagnostic()) }
        }
        viewModelScope.launch {
            while (isActive) {
                if (!monitor.foreground && active == null && resident == null && !state.value.busy) {
                    val version = wake.value
                    wake.first { it != version }
                    continue
                }
                periodicObservationCount++
                enforceSafety()
                if (!state.value.busy && resident != null && SystemClock.elapsedRealtime() - lastUse > resident!!.idleMillis) unload()
                if (capture.active && monitor.foreground) recordFrame()
                if (SystemClock.elapsedRealtime() - lastDiagnostic > 15_000) {
                    update { it.copy(diagnostic = diagnostic()) }; lastDiagnostic = SystemClock.elapsedRealtime()
                }
                val version = wake.value
                val interval = if (capture.active && monitor.foreground) 500L else if (active != null) 1_000L else if (resident != null) 2_000L else 15_000L
                withTimeoutOrNull(interval) { wake.first { it != version } }
            }
        }
    }
    private fun update(block: (ChatState) -> ChatState) {
        val before = state.value
        val requested = block(before)
        if (requested.busy != before.busy) workBusy = requested.busy
        state.value = requested.copy(busy = workBusy || pendingReleases > 0)
        if (before.busy != state.value.busy || before.profile != state.value.profile || before.capturing != state.value.capturing) wake.value++
    }
    private fun event(type: DiagnosticEventType) { capture.event(SystemClock.elapsedRealtime(), type) }
    private fun recordFrame() {
        if (!capture.active) return
        val now = SystemClock.elapsedRealtime()
        if (capture.expired(now)) { stopCapture("limite de 90 secondes atteinte"); return }
        val snapshot = monitor.snapshot()
        val process = monitor.processMemory()
        val config = active ?: resident ?: RuntimeConfig(ModelKind.SMALL, if (state.value.profile == Profile.TABLET_ECO) 512 else 768,
            if (state.value.profile == Profile.TABLET_ECO) 64 else 96, 1)
        val memory = policy.memory(config, snapshot, residency(snapshot, active ?: resident))
        val sampledAt = SystemClock.elapsedRealtime()
        capture.add(sampledAt, DiagnosticFrame(capture.elapsed(sampledAt), monitor.telemetry.phase, snapshot.pssBytes.takeIf { it > 0 },
            process.rssBytes, process.rssHighWaterBytes, snapshot.availableBytes, snapshot.totalBytes, snapshot.androidLowMemoryThreshold,
            snapshot.lowMemory, snapshot.batteryPercent, snapshot.thermalStatus, snapshot.thermalHeadroom, snapshot.foreground,
            memory.reserve, memory.estimatedBudget, memory.measuredCredit, memory.requiredAvailable, memory.margin, config,
            active != null || resident != null, state.value.profile, backend?.allocations, ProcessResources.summary()))
        if (sampledAt - lastDiagnosticUpdate >= 2000) {
            lastDiagnosticUpdate = sampledAt
            update { it.copy(diagnostic = diagnostic()) }
        }
    }
    fun startCapture() {
        if (!monitor.foreground || state.value.busy || capture.active) return
        startedWithResident = resident != null
        modelsAtCaptureStart = modelReferences()
        capture.start(SystemClock.elapsedRealtime())
        update { it.copy(capturing = true, status = "Capture de mesures démarrée ; aucun calcul lancé.") }
        recordFrame()
    }
    fun stopCapture(reason: String = "arrêt manuel de la capture") {
        if (!capture.active) return
        event(DiagnosticEventType.CAPTURE_STOPPED); capture.stop(reason)
        update { it.copy(capturing = false) }
    }
    private fun modelReferences() = ModelKind.entries.associateWith { store.expectedDigest(it) to store.file(it).length() }
    fun diagnosticJson(): String = DiagnosticJson.export(capture, state.value.profile, modelReferences(),
        BuildConfig.VERSION_NAME, BuildConfig.SOURCE_COMMIT, startedWithResident, modelsAtCaptureStart)
    fun exportDiagnostic(uri: Uri) {
        if (state.value.busy || capture.active) {
            update { it.copy(status = "Terminez la capture et le calcul avant l'export des mesures.") }; return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val text = diagnosticJson()
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        ?: error("Destination inaccessible")
                }
                update { it.copy(status = "Mesures exportées localement ; aucun budget d'admission modifié.") }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { update { it.copy(status = "Export incomplet : supprimer le fichier de destination puis réessayer.") } }
        }
    }
    fun unloadForDiagnostic() {
        if (state.value.busy) { update { it.copy(status = "Utilisez Arrêter pendant un calcul, puis attendez sa fin effective.") }; return }
        viewModelScope.launch {
            if (releaseForCatalogue()) update { it.copy(status = "Modèle déchargé ; aucun nouveau calcul lancé.") }
        }
    }
    private fun engine(): LanguageModel = backend ?: LlamaBackend().also { backend = it }
    private fun residency(snapshot: DeviceSnapshot, config: RuntimeConfig?): Residency? {
        val baseline = residentBaselinePss ?: return null
        if (baseline <= 0 || snapshot.pssBytes <= 0) return null
        if (config == null || baselineModel != config.model) return null
        return Residency(config.model, (snapshot.pssBytes - baseline).coerceAtLeast(0))
    }
    private fun diagnostic(): String {
        val s = monitor.snapshot()
        val file = store.file(ModelKind.SMALL)
        val size = if (file.isFile) "%.1f Mio".format(java.util.Locale.FRANCE, file.length() / 1048576.0) else "absent"
        return monitor.diagnostic(residency(s, active ?: resident), s) + "\nProfil sélectionné : ${state.value.profile}\n" + (backend?.allocationDiagnostic ?: "Allocations natives non mesurées") + "\nFichier GGUF 0.6B : $size (taille disque, pas PSS).\n" + catalogueDiagnostic + "\n" + ProcessResources.summary() + "\n" + capture.summary()
    }
    fun foreground(value: Boolean) {
        monitor.foreground = value
        wake.value++
        if (!value) { event(DiagnosticEventType.BACKGROUND); stopCapture("arrière-plan") }
        if (!value) { stop("Arrière-plan : arrêt et déchargement"); viewModelScope.launch { unload() } }
        else update { it.copy(diagnostic = diagnostic()) }
    }
    fun profile(value: Profile) {
        if (state.value.busy) stop("Changement de profil : arrêt demandé")
        update { it.copy(profile = value) }
        if (!state.value.busy) viewModelScope.launch { unload() }
    }
    private fun enforceSafety() {
        val config = active ?: resident ?: return
        val snapshot = monitor.snapshot()
        val decision = policy.decide(state.value.profile, snapshot, setOf(config.model), residency(snapshot, config))
        val pss = snapshot.pssBytes
        if (decision.config != config || pss > config.memoryBudget) {
            stop(if (pss > config.memoryBudget) "Budget mémoire application dépassé" else decision.reason)
            if (!state.value.busy) viewModelScope.launch { unload() }
        }
        // Imports also stop on severe thermal state or loss of foreground.
    }
    fun stop(reason: String = "Arrêt demandé") {
        event(DiagnosticEventType.STOP_REQUESTED)
        stopped.set(true); stoppingReason = reason
        backend?.requestStop()
        if (state.value.busy) update { it.copy(status = "$reason — attente de fin effective du calcul") }
    }
    private suspend fun unload() {
        // A queued unload is still work: reject new requests until the worker really finishes.
        pendingReleases++
        update { it }
        try {
            monitor.telemetry.phase(MemoryPhase.UNLOADING)
            recordFrame()
            val hadNativeModel = resident != null || backend?.allocations != null
            backend?.unload(); resident = null
            if (hadNativeModel) event(DiagnosticEventType.MODEL_UNLOADED)
            wake.value++
            recordFrame()
            baselineModel = null; residentBaselinePss = null
            monitor.snapshot()
            monitor.telemetry.phase(MemoryPhase.PASSIVE)

        } finally {
            pendingReleases--
            update { it }
        }
    }
    fun clearConversation() { clarifications = emptyList(); if (!state.value.busy) update { it.copy(messages = emptyList(), status = "Conversation effacée (mémoire vive uniquement).") } }
    suspend fun releaseForCatalogue(): Boolean {
        if (state.value.busy) return false
        update { it.copy(busy = true) }
        try { unload(); return monitor.foreground }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { update { it.copy(status = "Déchargement impossible : ${e.message}") }; return false }
        finally { update { it.copy(busy = false) } }
    }
    fun send(text: String) {
        if (state.value.busy || !monitor.foreground) return
        val original = text.trim()
        if (original.isEmpty() || original.length > 4_000) { update { it.copy(status = "Question vide ou trop longue (4 000 caractères maximum).") }; return }
        val choice = original.toIntOrNull()?.takeIf { it in 1..clarifications.size }
        val question = choice?.let { clarifications[it - 1] } ?: original
        clarifications = emptyList()
        stopped.set(false); stoppingReason = null
        update { it.copy(busy = true, status = "Recherche dans le catalogue local…") }
        viewModelScope.launch {
            var llm = false
            try {
                val start = SystemClock.elapsedRealtime()
                val route = withContext(Dispatchers.IO) {
                    val result = router.route(question)
                    catalogueDiagnostic = catalogue.stats() + "\nRecherche catalogue : ${SystemClock.elapsedRealtime() - start} ms ; aucune inférence sémantique."
                    result
                }
                check(!stopped.get() && monitor.foreground) { "Recherche arrêtée" }
                when (route) {
                    is CatalogueRoute.Llm -> { llm = true }
                    is CatalogueRoute.Answer -> {
                        unload()
                        check(!stopped.get() && monitor.foreground) { "Recherche arrêtée" }
                        val prefix = if (route.record.origin == CatalogueOrigin.SYNTHETIC) "[TEST SYNTHÉTIQUE] " else ""
                        update { it.copy(messages = (it.messages + Message("user", original) + Message("assistant", prefix + route.text)).takeLast(40),
                            status = "Catalogue — ${route.record.title}, révision ${route.record.revision}, variante ${route.variant + 1}") }
                    }
                    is CatalogueRoute.Clarify -> {
                        unload()
                        check(!stopped.get() && monitor.foreground) { "Recherche arrêtée" }
                        clarifications = route.suggestedQueries
                        update { it.copy(messages = (it.messages + Message("user", original) + Message("assistant", route.text)).takeLast(40), status = "Clarification requise — aucun LLM chargé") }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { update { it.copy(status = stoppingReason ?: "Catalogue indisponible : ${e.message}") } }
            finally { update { it.copy(busy = false, diagnostic = diagnostic()) } }
            if (llm && !stopped.get() && monitor.foreground) sendLlm(original)
        }
    }
    private fun sendLlm(text: String) {
        if (state.value.busy) return
        val question = text.trim()
        if (question.isEmpty() || question.length > 4_000) { update { it.copy(status = "Question vide ou trop longue (4 000 caractères maximum).") }; return }
        val snapshot = monitor.snapshot()
        val decision = policy.decide(state.value.profile, snapshot, store.available(), residency(snapshot, resident))
        val config = decision.config ?: run { event(DiagnosticEventType.ADMISSION_REFUSED); recordFrame(); update { it.copy(status = decision.reason, diagnostic = diagnostic()) }; return }
        monitor.telemetry.begin()
        stopped.set(false); stoppingReason = null
        // No suspension between arming the flag and marking the request active.
        val model = try { engine().also { it.prepareOperation() } } catch (e: Exception) {
            update { it.copy(status = "Moteur indisponible : ${e.message}") }; return
        }
        val messages = (state.value.messages.takeLast(6) + Message("user", question)).takeLast(7)
        active = config
        wake.value++
        update { it.copy(busy = true, status = "${decision.reason} — vérification et chargement…") }
        viewModelScope.launch {
            try {
                if (resident != config) {
                    unload()
                    val admitted = policy.decide(state.value.profile, monitor.snapshot(), setOf(config.model))
                    check(admitted.config == config) { admitted.reason }
                    residentBaselinePss = monitor.snapshot().pssBytes.takeIf { it > 0 }; baselineModel = config.model
                    monitor.telemetry.phase(MemoryPhase.VERIFYING)
                    recordFrame()
                    val path = withContext(Dispatchers.IO) {
                        ProcessResources.requireHeavy(resourceOwner, Module.MODEL_IMPORT, "vérification SHA-256").use {
                            store.verify(config.model) { stopped.get() }
                        }
                    }
                    check(!stopped.get()) { "Opération arrêtée" }
                    monitor.telemetry.phase(MemoryPhase.LOADING)
                    recordFrame()
                    model.load(path, config); resident = config
                    event(DiagnosticEventType.MODEL_LOADED); wake.value++
                    monitor.snapshot(); recordFrame()
                }
                // An idle/background unload can have been queued before this request.
                // The backend rechecks its actual state on the same native worker.
                model.load(store.file(config.model).absolutePath, config); resident = config
                check(!stopped.get()) { "Opération arrêtée" }
                monitor.telemetry.phase(MemoryPhase.GENERATING)
                update { it.copy(status = "Génération locale — ${config.model.name}") }
                recordFrame()
                val answer = model.generate(GenerationRequest(messages, config)).trim()
                monitor.snapshot()
                check(!stopped.get()) { "Opération arrêtée" }
                check(answer.isNotEmpty()) { "Le modèle n'a produit aucune réponse" }
                event(DiagnosticEventType.GENERATION_SUCCEEDED); recordFrame()
                update { it.copy(messages = (messages + Message("assistant", answer)).takeLast(40), status = "Réponse locale. Vérifie les informations importantes.") }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                event(DiagnosticEventType.OPERATION_ERROR); recordFrame()
                model.requestStop(); unload()
                update { it.copy(status = stoppingReason ?: "Erreur locale : ${e.message}") }
            } finally {
                active = null; wake.value++
                lastUse = SystemClock.elapsedRealtime()
                withContext(NonCancellable) { if (stopped.get() || !monitor.foreground) unload() }
                monitor.telemetry.phase(MemoryPhase.PASSIVE)
                update { it.copy(busy = false, diagnostic = diagnostic()) }
            }
        }
    }
    fun importModel(uri: Uri, kind: ModelKind, digest: String) {
        if (state.value.busy) return
        if (!monitor.foreground) return
        val s = monitor.snapshot()
        val thermal = s.thermalStatus
        val battery = s.batteryPercent
        if (thermal == null || thermal !in 0..2 || battery == null || battery !in 10..100 || s.lowMemory) {
            update { it.copy(status = "Import différé : conditions de sécurité insuffisantes") }; return
        }
        stopped.set(false); stoppingReason = null
        update { it.copy(busy = true, status = "Import local et vérification SHA-256…") }
        viewModelScope.launch {
            try {
                unload()
                withContext(Dispatchers.IO) {
                    ProcessResources.requireHeavy(resourceOwner, Module.MODEL_IMPORT, "import GGUF", 128L * 1024 * 1024).use {
                    store.import(uri, kind, digest) {
                        val current = monitor.snapshot(samplePss = false)
                        val t = current.thermalStatus
                        val b = current.batteryPercent
                        stopped.get() || !current.foreground || t == null || t !in 0..2 ||
                            b == null || b !in 10..100 || current.lowMemory
                    }
                    }
                }
                update { it.copy(status = "Modèle importé. Aucun calcul d'inférence lancé.") }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { update { it.copy(status = stoppingReason ?: "Import refusé : ${e.message}") } }
            finally { update { it.copy(busy = false, models = store.available(), diagnostic = monitor.diagnostic()) } }
        }
    }
    override fun onCleared() {
        stopped.set(true); backend?.requestStop(); monitor.close()
        CoroutineScope(Dispatchers.IO).launch { catalogue.close() }
        val model = backend
        // This cleanup scope survives the cancelled ViewModel scope and queues release behind native work.
        if (model != null) CoroutineScope(Dispatchers.IO).launch { model.close() }
        super.onCleared()
    }
}
