package fr.jarvis.llm

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.security.Security

/** A01 read-only inventory. No KeyPairGenerator.generateKeyPair, no key material. */
class A01DiagnosticActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val providers = Security.getProviders().toList()
        val keystore = providers.find { it.name == "AndroidKeyStore" }
        val ed = providers.filter { provider ->
            provider.services.any { service ->
                service.type.equals("KeyPairGenerator", true) &&
                    (service.algorithm.equals("Ed25519", true) || service.algorithm.equals("EdDSA", true))
            }
        }.map { it.name }
        val report = """
            JARVIS A01 — Diagnostic en lecture seule

            Android : ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})
            Appareil : ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}

            AndroidKeyStore disponible : ${keystore != null}
            Fournisseurs KeyPairGenerator Ed25519/EdDSA : ${ed.ifEmpty { listOf("aucun annoncé") }.joinToString()}
            Ed25519 annoncé par AndroidKeyStore : ${"AndroidKeyStore" in ed}

            IMPORTANT
            Ce diagnostic ne prouve PAS que la clé serait non exportable ni protégée matériellement.
            Aucune clé générée, aucune signature, aucune écriture dans AndroidKeyStore.
            A01 reste fermée jusqu'à validation indépendante de la clé publique.
        """.trimIndent()
        val text = TextView(this).apply { this.text = report; textSize = 16f; setPadding(32,32,32,32); setTextIsSelectable(true) }
        setContentView(ScrollView(this).apply { addView(text) })
    }
}
