package fr.jarvis.llm.catalogue

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.os.StatFs
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import fr.jarvis.domain.catalogue.*
import fr.jarvis.domain.RuntimeConfig
import fr.jarvis.domain.ModelKind
import fr.jarvis.domain.SafetyPolicy
import fr.jarvis.llm.DeviceMonitor
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/** Catalogue editor inside the same offline application. All SQLite/SAF work is on IO. */
class CatalogueActivity : ComponentActivity() {
    private lateinit var store: CatalogueStore
    private lateinit var monitor: DeviceMonitor
    private lateinit var status: TextView
    private lateinit var list: LinearLayout
    private val actions = mutableListOf<Button>()
    private val cancelled = AtomicBoolean(false)
    private var busy = false
    private var offset = 0
    private val importer = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) preview(uri) }
    private val exporter = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) export(uri) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = CatalogueStore(applicationContext)
        monitor = DeviceMonitor(this) { if (busy && !safe()) cancelled.set(true) }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 48, 20, 24) }
        root.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(20, insets.systemWindowInsetTop + 16, 20, insets.systemWindowInsetBottom + 12); insets
        }
        root.addView(TextView(this).apply { text = "Catalogue local"; textSize = 22f })
        status = TextView(this); root.addView(status)
        fun button(text: String, block: () -> Unit) = Button(this).apply { this.text = text; setOnClickListener { if (!busy) block() }; actions.add(this) }
        val bar = LinearLayout(this)
        bar.addView(button("Ajouter") { edit(null) }, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(button("Importer JSON") { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(button("Exporter JSON") { exporter.launch("jarvis-catalogue.json") }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(bar)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        val pages = LinearLayout(this)
        pages.addView(button("Précédent") { offset = (offset - 50).coerceAtLeast(0); refresh() }, LinearLayout.LayoutParams(0, -2, 1f))
        pages.addView(button("Suivant") { offset += 50; refresh() }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(pages)
        setContentView(root)
        lifecycleScope.launch { refresh() }
    }
    private fun safe(): Boolean {
        val s = monitor.snapshot(samplePss = false)
        val reserve = SafetyPolicy().memory(RuntimeConfig(ModelKind.SMALL, 512, 64, 1), s).reserve
        return (s.thermalHeadroom?.let { it < 1f } ?: true) && s.availableBytes >= reserve + 128L * 1024 * 1024 && s.foreground && !s.lowMemory && s.thermalStatus?.let { it in 0..2 } == true && s.batteryPercent?.let { it in 10..100 } == true &&
            StatFs(filesDir.path).availableBytes >= 256L * 1024 * 1024
    }
    private fun checkSafe() { check(!cancelled.get() && safe()) { "Conditions de sécurité insuffisantes ou opération arrêtée" } }
    private fun action(label: String, work: suspend () -> Unit) {
        if (busy) return
        busy = true; cancelled.set(false); status.text = label; actions.forEach { it.isEnabled = false }
        lifecycleScope.launch {
            try { checkSafe(); work() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { status.text = "Opération refusée : ${e.message}" }
            finally { busy = false; actions.forEach { it.isEnabled = true } }
        }
    }
    private suspend fun loadPage(): Pair<List<AnswerRecord>, String> = withContext(Dispatchers.IO) { store.list(offset) to store.stats() }
    private fun refresh() { lifecycleScope.launch {
        try { renderPage(loadPage()) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { status.text = "Catalogue indisponible : ${e.message}" }
    } }
    private fun renderPage(page: Pair<List<AnswerRecord>, String>) {
        status.text = page.second + "\nPage ${offset / 50 + 1} ; toucher une réponse pour la modifier."
        list.removeAllViews()
        page.first.forEach { record ->
            list.addView(Button(this).apply {
                text = "${if (record.origin == CatalogueOrigin.SYNTHETIC) "[TEST SYNTHÉTIQUE] " else ""}${record.title}\n${record.category} · r${record.revision} · ${record.variants.size} variante(s)"
                setOnClickListener { if (!busy) edit(record) }
            })
        }
    }
    private fun preview(uri: Uri) = action("Lecture du catalogue local…") {
        val records = withContext(Dispatchers.IO) { contentResolver.openInputStream(uri)?.use { CatalogueJson.read(it) { cancelled.get() || !safe() } } ?: error("Fichier inaccessible") }
        checkSafe()
        // Preview before mutation. Only this bounded file is retained until the dialog closes.
        AlertDialog.Builder(this).setTitle("Importer ${records.size} réponses ?")
            .setMessage("Fusion par identifiant et révision ; une collision annule tout l'import. ${records.count { it.origin == CatalogueOrigin.SYNTHETIC }} entrées SYNTHÉTIQUES de test. Aucun modèle ne sera chargé.")
            .setNegativeButton("Annuler", null).setPositiveButton("Importer") { _, _ ->
                action("Import transactionnel…") {
                    val count = withContext(Dispatchers.IO) { store.merge(records) { cancelled.get() || !safe() } }
                    renderPage(loadPage()); status.text = "$count réponse(s) mise(s) à jour. " + status.text
                }
            }.show()
    }
    private fun export(uri: Uri) = action("Export local…") {
        try { withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri, "wt")?.use { store.export(it) { cancelled.get() || !safe() } } ?: error("Destination inaccessible") } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { throw IllegalStateException("Export incomplet : supprimez le fichier de destination avant de réessayer", e) }
        status.text = "Export terminé. Les suppressions logiques sont incluses pour empêcher une restauration par une ancienne révision."
    }
    private fun edit(old: AnswerRecord?) {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 12, 20, 12) }
        fun field(label: String, initial: String, multiline: Boolean = false): EditText {
            body.addView(TextView(this).apply { text = label })
            return EditText(this).apply { setText(initial); if (multiline) { minLines = 2; maxLines = 4 } else isSingleLine = true; body.addView(this) }
        }
        val id = field("Identifiant stable (a-z, chiffres, . _ -)", old?.id ?: "").apply { isEnabled = old == null }
        val intent = field("Intention stable", old?.intent ?: "")
        val title = field("Titre", old?.title ?: "")
        val category = field("Catégorie", old?.category ?: "")
        val examples = field("Formulations : une par ligne", old?.examples?.joinToString("\n") ?: "", true)
        val variants = field("Variantes de sens équivalent : séparer par une ligne --- ; une seule suffit", old?.variants?.joinToString("\n---\n") ?: "", true)
        val dialog = AlertDialog.Builder(this).setTitle(if (old == null) "Ajouter une réponse" else "Modifier r${old.revision}")
            .setView(ScrollView(this).apply { addView(body) }).setNegativeButton("Annuler", null).setPositiveButton("Enregistrer", null)
            .apply { if (old != null) setNeutralButton("Supprimer") { _, _ ->
                AlertDialog.Builder(this@CatalogueActivity).setMessage("Retirer cette réponse de la recherche ? Son historique et une suppression versionnée restent dans SQLite et l'export.")
                    .setNegativeButton("Annuler", null).setPositiveButton("Supprimer") { _, _ -> action("Suppression…") { withContext(Dispatchers.IO) { store.delete(old.id, old.revision) }; renderPage(loadPage()) } }.show()
            } }.create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                val record = AnswerRecord(id.text.toString().trim(), intent.text.toString().trim(), title.text.toString().trim(), category.text.toString().trim(),
                    (old?.revision ?: 0) + 1, examples.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() },
                    variants.text.toString().split(Regex("(?m)^---\\s*$")).map { it.trim() }.filter { it.isNotEmpty() }, CatalogueOrigin.USER)
                action("Enregistrement…") { withContext(Dispatchers.IO) { store.save(record, old?.revision) }; renderPage(loadPage()) }
                dialog.dismiss()
            } catch (e: IllegalArgumentException) { title.error = "Réponse invalide : ${e.message}" }
        } }
        dialog.show()
    }
    override fun onStart() { monitor.foreground = true; super.onStart() }
    override fun onStop() { monitor.foreground = false; cancelled.set(true); super.onStop() }
    override fun onDestroy() { monitor.close(); CoroutineScope(Dispatchers.IO).launch { store.close() }; super.onDestroy() }
}
