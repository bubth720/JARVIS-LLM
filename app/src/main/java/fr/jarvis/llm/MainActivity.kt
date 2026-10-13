package fr.jarvis.llm

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import fr.jarvis.domain.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: ChatViewModel by viewModels()
    private lateinit var conversation: TextView
    private lateinit var status: TextView
    private lateinit var diagnostic: TextView
    private lateinit var input: EditText
    private lateinit var send: Button
    private lateinit var import: Button
    private lateinit var clear: Button
    private lateinit var stop: Button
    private var pending: Pair<ModelKind, String>? = null
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val reference = pending
        if (uri != null && reference != null) vm.importModel(uri, reference.first, reference.second)
        pending = null
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString("pendingKind")?.let { name ->
            pending = ModelKind.valueOf(name) to (savedInstanceState.getString("pendingHash") ?: "")
        }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 20, 20, 12) }
        root.setOnApplyWindowInsetsListener { v, insets ->
            v.setPadding(20, insets.systemWindowInsetTop + 16, 20, insets.systemWindowInsetBottom + 12); insets
        }
        root.addView(TextView(this).apply { text = "JARVIS LLM — prototype local"; textSize = 22f })
        val profiles = Spinner(this)
        val labels = listOf("ÉCO", "ÉQUILIBRÉ", "AUTO", "PLEINE PUISSANCE (non qualifiée)")
        profiles.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        profiles.setSelection(vm.ui.value.profile.ordinal)
        profiles.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = Profile.entries[position]
                if (selected != vm.ui.value.profile) vm.profile(selected)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        root.addView(profiles)
        status = TextView(this).apply { textSize = 14f }; root.addView(status)
        diagnostic = TextView(this).apply { textSize = 11f; setTextIsSelectable(true) }; root.addView(diagnostic)
        val buttons = LinearLayout(this)
        import = Button(this).apply { text = "Importer"; setOnClickListener { importDialog() } }
        clear = Button(this).apply { text = "Effacer"; setOnClickListener { vm.clearConversation() } }
        stop = Button(this).apply { text = "Arrêter"; setOnClickListener { vm.stop() } }
        listOf(import, clear, stop).forEach { buttons.addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        root.addView(buttons)
        conversation = TextView(this).apply { textSize = 16f; setTextIsSelectable(true) }
        val scroll = ScrollView(this).apply { addView(conversation) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        input = EditText(this).apply { hint = "Message en français"; maxLines = 4; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE }
        root.addView(input)
        send = Button(this).apply { text = "Envoyer"; setOnClickListener { vm.send(input.text.toString()) } }; root.addView(send)
        setContentView(root)
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.ui.collect { s ->
                status.text = "${s.status}\nModèles importés : ${s.models.joinToString { if (it == ModelKind.SMALL) "0.6B" else "1.7B" }.ifEmpty { "aucun" }}"
                diagnostic.text = s.diagnostic
                conversation.text = s.messages.joinToString("\n\n") { "${if (it.role == "user") "Vous" else "JARVIS"} : ${it.text}" }
                send.isEnabled = !s.busy; import.isEnabled = !s.busy; clear.isEnabled = !s.busy; stop.isEnabled = s.busy
            }
        } }
    }
    private fun importDialog() {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 12, 24, 0) }
        body.addView(TextView(this).apply { text = "GGUF Q4_K_M obtenu localement. Saisis le SHA-256 issu d'une référence fiable indépendante du fichier. Aucun téléchargement n'est effectué." })
        val kind = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Qwen3 0.6B — repli", "Qwen3 1.7B — principal")) }
        val hash = EditText(this).apply { hint = "SHA-256 attendu (64 caractères)"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS }
        body.addView(kind); body.addView(hash)
        val dialog = AlertDialog.Builder(this).setTitle("Importer un modèle local").setView(body).setNegativeButton("Annuler", null).setPositiveButton("Choisir le fichier", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val digest = hash.text.toString().trim()
            if (!digest.matches(Regex("[a-fA-F0-9]{64}"))) hash.error = "Empreinte SHA-256 complète requise"
            else { pending = ModelKind.entries[kind.selectedItemPosition] to digest; dialog.dismiss(); picker.launch(arrayOf("application/octet-stream", "*/*")) }
        } }
        dialog.show()
    }
    override fun onStart() { super.onStart(); vm.foreground(true) }
    override fun onStop() { vm.foreground(false); super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) {
        pending?.let { outState.putString("pendingKind", it.first.name); outState.putString("pendingHash", it.second) }
        super.onSaveInstanceState(outState)
    }
}
