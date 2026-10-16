package fr.jarvis.fixture.llm
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.*
import fr.jarvis.domain.ModelKind
/** Local URI selection is passive. Copy/SHA work requires a real CORE grant AND hardware admission. */
class FixtureActivity : Activity() {
    private lateinit var text: TextView
    private lateinit var sha: EditText
    private lateinit var model: Spinner
    override fun onCreate(state: Bundle?) { super.onCreate(state)
        val body=LinearLayout(this); body.orientation=LinearLayout.VERTICAL
        text=TextView(this); text.text="FIXTURE 003E — aucun chargement sans arbitre CORE réel.\nAucune installation personnelle à modifier."
        sha=EditText(this); sha.hint="SHA-256 fiable du GGUF public (64 caractères)"; sha.isSingleLine=true
        model=Spinner(this); model.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,ModelKind.entries.map { it.name })
        val choose=Button(this); choose.text="Préparer une référence GGUF locale"
        choose.setOnClickListener {
            if (!sha.text.toString().matches(Regex("[a-fA-F0-9]{64}"))) { text.text="SHA-256 invalide"; return@setOnClickListener }
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),1)
        }
        body.addView(text); body.addView(model); body.addView(sha); body.addView(choose); setContentView(body)
    }
    override fun onActivityResult(request: Int,result: Int,data: Intent?) { super.onActivityResult(request,result,data)
        if(request != 1 || result != RESULT_OK)return
        val uri=data?.data ?: return
        val hash=sha.text.toString().lowercase(); if(!hash.matches(Regex("[a-f0-9]{64}")))return
        try {
            contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val kind=ModelKind.entries[model.selectedItemPosition]
            check(getSharedPreferences("fixture-staged-public-model",MODE_PRIVATE).edit().putString(kind.name+".uri",uri.toString()).putString(kind.name+".sha",hash).commit())
            text.text="Référence privée de fixture enregistrée. Aucun fichier copié, modèle chargé ou test lancé. L'arbitre CORE réel reste obligatoire."
        } catch (_: Exception) { text.text="Référence refusée" }
    }
    override fun onResume() { super.onResume(); (application as FixtureApplication).monitor.foreground=true }
}
