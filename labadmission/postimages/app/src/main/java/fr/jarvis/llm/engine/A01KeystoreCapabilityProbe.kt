package fr.jarvis.llm.engine

import java.security.KeyPairGenerator
import java.security.Provider
import java.security.Security

/**
 * Read-only capability inventory for A01. Never generates a key, signs a
 * payload, imports credentials or changes Android Keystore state.
 * Run on the actual device, not as proof from a CI emulator.
 */
internal object A01KeystoreCapabilityProbe {
    data class Report(
        val androidKeystorePresent: Boolean,
        val ed25519KeyPairGeneratorProviders: List<String>,
        val androidKeystoreEd25519Advertised: Boolean,
        val hardwareNonExportabilityVerified: Boolean = false,
        val rootProvisioned: Boolean = false
    )
    fun inspect(): Report {
        val providers: List<Provider> = Security.getProviders().toList()
        val candidates = providers.filter { provider ->
            provider.services.any { service ->
                service.type.equals("KeyPairGenerator", ignoreCase = true) &&
                    (service.algorithm.equals("Ed25519", ignoreCase = true) ||
                     service.algorithm.equals("EdDSA", ignoreCase = true))
            }
        }.map { it.name }
        return Report(
            androidKeystorePresent = providers.any { it.name == "AndroidKeyStore" },
            ed25519KeyPairGeneratorProviders = candidates,
            androidKeystoreEd25519Advertised = "AndroidKeyStore" in candidates
        )
    }
}
