package fr.jarvis.domain.resources

/** Cooperative process-local ownership. Declared envelopes are estimates, never measured RAM. */
enum class ResourceClass { HEAVY, MODEL }
enum class Module { LLM, MODEL_IMPORT, CATALOGUE, CORE, VOICE, INTERFACE, SERVICE }
data class ResourceRequest(val owner: String, val module: Module, val kind: ResourceClass,
                           val operation: String, val declaredEnvelopeBytes: Long = 0, val modelKey: String? = null) {
    init { require(owner.isNotBlank() && owner.length <= 120 && operation.length in 1..160 && declaredEnvelopeBytes >= 0)
        require(kind != ResourceClass.MODEL || !modelKey.isNullOrBlank()) }
}
data class ResourceState(val heavy: ResourceRequest?, val model: ResourceRequest?)
sealed interface ResourceGrant {
    data class Granted(val lease: ResourceLease) : ResourceGrant
    data class Refused(val reason: String) : ResourceGrant
}
class ResourceLease internal constructor(private val release: () -> Unit) : AutoCloseable {
    // Ownership release is idempotent, including late completion of an old operation.
    @Synchronized override fun close() = release()
}
class ResourceCoordinator {
    private var nextId = 0L
    private var heavy: Pair<Long, ResourceRequest>? = null
    private var model: Pair<Long, ResourceRequest>? = null
    @Synchronized fun acquire(request: ResourceRequest): ResourceGrant {
        val reason = when (request.kind) {
            ResourceClass.HEAVY -> when {
                heavy != null -> "Traitement lourd déjà actif : ${heavy!!.second.module}/${heavy!!.second.operation}"
                model != null && model!!.second.owner != request.owner -> "Libérer le modèle résident avant un traitement lourd d'un autre propriétaire"
                else -> null
            }
            ResourceClass.MODEL -> when {
                model != null -> "Un modèle est déjà chargé ou en cours de chargement"
                heavy?.second?.owner != request.owner -> "Le chargement exige un accès lourd exclusif du même propriétaire"
                else -> null
            }
        }
        if (reason != null) return ResourceGrant.Refused(reason)
        val id = ++nextId
        if (request.kind == ResourceClass.HEAVY) heavy = id to request else model = id to request
        return ResourceGrant.Granted(ResourceLease { release(request.kind, id) })
    }
    @Synchronized private fun release(kind: ResourceClass, id: Long) {
        if (kind == ResourceClass.HEAVY && heavy?.first == id) heavy = null
        if (kind == ResourceClass.MODEL && model?.first == id) model = null
    }
    @Synchronized fun snapshot() = ResourceState(heavy?.second, model?.second)
}
