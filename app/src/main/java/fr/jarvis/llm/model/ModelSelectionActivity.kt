package fr.jarvis.llm.model

import android.app.AlertDialog
import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import fr.jarvis.domain.ModelKind
import fr.jarvis.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** Catalogue is opt-in metadata management only, not a second inference entry point. */
class ModelSelectionActivity : ComponentActivity() {
    private lateinit var catalog: VerifiedModelCatalog
    private lateinit var importer: ModelImportCoordinator
    private lateinit var content: LinearLayout
    private val cancel = AtomicBoolean(false)
    private var busy = false
    private lateinit var monitor: fr.jarvis.llm.DeviceMonitor
    private var pending: ModelDescriptor? = null
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val model = pending; pending = null
        if (uri != null && model != null) operate { importer.import(uri, model) { cancel.get() || !safeImport() }; "Copie vérifiée, aucune inférence lancée" }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        monitor = fr.jarvis.llm.DeviceMonitor(this) { cancel.set(true) }
        catalog = VerifiedModelCatalog(this); importer = ModelImportCoordinator(this, catalog)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,24,24,24) }
        setContentView(ScrollView(this).apply { addView(content) }); render()
    }
    private fun render(message: String = "") {
        content.removeAllViews()
        content.addView(TextView(this).apply { text = "Modèles locaux — catalogue expérimental\nSélection persistante de catalogue uniquement ; le chat historique garde sa politique actuelle. Aucun chargement automatique.\n$message" })
        content.addView(Button(this).apply { text = "Importer une copie GGUF"; isEnabled = !busy; setOnClickListener { importDialog() } })
        content.addView(Button(this).apply { text = "Désélectionner"; isEnabled = !busy; setOnClickListener { catalog.select(null); render() } })
        content.addView(Button(this).apply { text = "Annuler l'import en cours"; isEnabled = busy; setOnClickListener { cancel.set(true) } })
        val selected = catalog.selected()
        catalog.list().forEach { model ->
            val compatible = ModelCompatibilityChecker.check(model, importer.file(model.sha256).isFile)
            content.addView(TextView(this).apply { text = "${model.kind} ${model.sha256.take(12)} — ${model.sizeBytes} octets\n${compatible.state}: ${compatible.reason}\nLicence déclarée : ${model.license}" })
            content.addView(Button(this).apply {
                text = if (selected == model.sha256) "Sélectionné (catalogue)" else "Sélectionner (sans charger)"
                isEnabled = !busy && compatible.state != ModelQualificationState.INCOMPATIBLE
                setOnClickListener { catalog.select(model.sha256); render() }
            })
            content.addView(Button(this).apply { text = "Supprimer cette copie"; isEnabled = !busy; setOnClickListener {
                AlertDialog.Builder(this@ModelSelectionActivity).setMessage("Supprimer seulement la copie du catalogue ? Le fichier SAF original et les modèles historiques sont conservés.")
                    .setNegativeButton("Annuler", null).setPositiveButton("Supprimer") { _, _ -> operate { importer.deleteCopy(model.sha256); "Copie supprimée volontairement" } }.show()
            } })
        }
    }
    private fun operate(action: () -> String) {
        if (busy) return
        busy = true; cancel.set(false); render("Opération locale en cours")
        lifecycleScope.launch {
            val result = try { withContext(Dispatchers.IO) { action() } } catch (e: Exception) { "Refus : ${e.message}" }
            busy = false; if (!isDestroyed) render(result) else catalog.close()
        }
    }
    private fun importDialog() {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun field(h: String) = EditText(this).apply { hint = h; body.addView(this) }
        val kind = Spinner(this).apply { adapter = ArrayAdapter(this@ModelSelectionActivity, android.R.layout.simple_spinner_dropdown_item, ModelKind.entries.map { it.name }); body.addView(this) }
        val sha = field("SHA-256 indépendant (64 caractères)")
        val size = field("Taille exacte en octets")
        val license = field("Licence et référence de provenance publique")
        body.addView(TextView(this).apply { text = "Pilote Qwen3 Q4_K_M seulement. La déclaration ne qualifie pas l'exécution. Ne saisir aucune donnée personnelle. Aucun poids téléchargé." })
        val d = AlertDialog.Builder(this).setTitle("Importer sans charger").setView(body).setNegativeButton("Annuler", null).setPositiveButton("Fichier local", null).create()
        d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                pending = ModelDescriptor(sha.text.toString().trim().lowercase(), size.text.toString().toLong(), ModelKind.entries[kind.selectedItemPosition], "qwen3", "Q4_K_M", license.text.toString(), "SAF local / provenance déclarée", System.currentTimeMillis())
                d.dismiss(); picker.launch(arrayOf("*/*"))
            } catch (_: Exception) { sha.error = "Empreinte, taille ou licence invalide" }
        } }; d.show()
    }
    private fun safeImport(): Boolean {
        val s = monitor.snapshot(samplePss = false)
        val t = s.thermalStatus; val b = s.batteryPercent
        return s.foreground && t != null && t in 0..2 && b != null && b in 10..100 && !s.lowMemory
    }
    override fun onStart() { monitor.foreground = true; super.onStart() }
    override fun onStop() { cancel.set(true); monitor.foreground = false; super.onStop() }
    override fun onDestroy() { cancel.set(true); monitor.close(); if (!busy) catalog.close(); super.onDestroy() }
}
