package fr.jarvis.llm

import android.app.Application
import android.net.Uri
import android.os.Debug
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.jarvis.domain.*
import fr.jarvis.llm.engine.LlamaBackend
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

data class ChatState(val messages: List<Message> = emptyList(), val status: String = "Importe un modèle GGUF vérifié pour commencer.",
                     val busy: Boolean = false, val profile: Profile = Profile.AUTO, val models: Set<ModelKind> = emptySet(),
                     val diagnostic: String = "")

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val state = MutableStateFlow(ChatState())
    val ui = state.asStateFlow()
    private val policy = SafetyPolicy()
    private val store = ModelStore(app)
    private var backend: LanguageModel? = null
    private val stopped = AtomicBoolean(false)
    private var resident: RuntimeConfig? = null
    private var active: RuntimeConfig? = null
    private var lastUse = 0L
    private var lastDiagnostic = 0L
    private var stoppingReason: String? = null
    private val monitor = DeviceMonitor(app) { enforceSafety() }
    init {
        update { it.copy(models = store.available(), diagnostic = monitor.diagnostic()) }
        viewModelScope.launch {
            while (isActive) {
                enforceSafety()
                if (!state.value.busy && resident != null && SystemClock.elapsedRealtime() - lastUse > resident!!.idleMillis) unload()
                if (SystemClock.elapsedRealtime() - lastDiagnostic > 15_000) {
                    update { it.copy(diagnostic = monitor.diagnostic()) }; lastDiagnostic = SystemClock.elapsedRealtime()
                }
                delay(2_000)
            }
        }
    }
    private fun update(block: (ChatState) -> ChatState) { state.value = block(state.value) }
    private fun engine(): LanguageModel = backend ?: LlamaBackend().also { backend = it }
    fun foreground(value: Boolean) {
        monitor.foreground = value
        if (!value) { stop("Arrière-plan : arrêt et déchargement"); viewModelScope.launch { unload() } }
        else update { it.copy(diagnostic = monitor.diagnostic()) }
    }
    fun profile(value: Profile) {
        if (state.value.busy) stop("Changement de profil : arrêt demandé")
        update { it.copy(profile = value) }
        if (!state.value.busy) viewModelScope.launch { unload() }
    }
    private fun enforceSafety() {
        val config = active ?: resident ?: return
        val snapshot = monitor.snapshot()
        val decision = policy.decide(state.value.profile, snapshot, setOf(config.model), config.memoryBudget)
        val pss = Debug.getPss() * 1024L
        if (decision.config != config || pss > config.memoryBudget) {
            stop(if (pss > config.memoryBudget) "Budget mémoire application dépassé" else decision.reason)
            if (!state.value.busy) viewModelScope.launch { unload() }
        }
        // Imports also stop on severe thermal state or loss of foreground.
    }
    fun stop(reason: String = "Arrêt demandé") {
        stopped.set(true); stoppingReason = reason
        backend?.requestStop()
        if (state.value.busy) update { it.copy(status = "$reason — attente de fin effective du calcul") }
    }
    private suspend fun unload() {
        backend?.unload(); resident = null
    }
    fun clearConversation() { if (!state.value.busy) update { it.copy(messages = emptyList(), status = "Conversation effacée (mémoire vive uniquement).") } }
    fun send(text: String) {
        if (state.value.busy) return
        val question = text.trim()
        if (question.isEmpty() || question.length > 4_000) { update { it.copy(status = "Question vide ou trop longue (4 000 caractères maximum).") }; return }
        val decision = policy.decide(state.value.profile, monitor.snapshot(), store.available(), resident?.memoryBudget ?: 0)
        val config = decision.config ?: run { update { it.copy(status = decision.reason) }; return }
        stopped.set(false); stoppingReason = null
        // No suspension between arming the flag and marking the request active.
        val model = try { engine().also { it.prepareOperation() } } catch (e: Exception) {
            update { it.copy(status = "Moteur indisponible : ${e.message}") }; return
        }
        val messages = (state.value.messages.takeLast(6) + Message("user", question)).takeLast(7)
        active = config
        update { it.copy(busy = true, status = "${decision.reason} — vérification et chargement…") }
        viewModelScope.launch {
            try {
                if (resident != config) {
                    unload()
                    val admitted = policy.decide(state.value.profile, monitor.snapshot(), setOf(config.model))
                    check(admitted.config == config) { admitted.reason }
                    val path = withContext(Dispatchers.IO) { store.verify(config.model) { stopped.get() } }
                    check(!stopped.get()) { "Opération arrêtée" }
                    model.load(path, config); resident = config
                }
                // An idle/background unload can have been queued before this request.
                // The backend rechecks its actual state on the same native worker.
                model.load(store.file(config.model).absolutePath, config); resident = config
                check(!stopped.get()) { "Opération arrêtée" }
                update { it.copy(status = "Génération locale — ${config.model.name}") }
                val answer = model.generate(GenerationRequest(messages, config)).trim()
                check(!stopped.get()) { "Opération arrêtée" }
                check(answer.isNotEmpty()) { "Le modèle n'a produit aucune réponse" }
                update { it.copy(messages = (messages + Message("assistant", answer)).takeLast(40), status = "Réponse locale. Vérifie les informations importantes.") }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                model.requestStop(); unload()
                update { it.copy(status = stoppingReason ?: "Erreur locale : ${e.message}") }
            } finally {
                active = null; lastUse = SystemClock.elapsedRealtime()
                withContext(NonCancellable) { if (stopped.get() || !monitor.foreground) unload() }
                update { it.copy(busy = false, diagnostic = monitor.diagnostic()) }
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
                    store.import(uri, kind, digest) {
                        val current = monitor.snapshot()
                        val t = current.thermalStatus
                        val b = current.batteryPercent
                        stopped.get() || !current.foreground || t == null || t !in 0..2 ||
                            b == null || b !in 10..100 || current.lowMemory
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
        val model = backend
        // This cleanup scope survives the cancelled ViewModel scope and queues release behind native work.
        if (model != null) CoroutineScope(Dispatchers.IO).launch { model.close() }
        super.onCleared()
    }
}
