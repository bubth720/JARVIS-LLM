package fr.jarvis.llm

import fr.jarvis.domain.resources.*

/** One broker for this application process only. No authority over other JARVIS applications. */
object ProcessResources {
    val coordinator = ResourceCoordinator()
    fun requireHeavy(owner: String, module: Module, operation: String, envelope: Long = 0): ResourceLease =
        requireLease(ResourceRequest(owner, module, ResourceClass.HEAVY, operation, envelope))
    fun requireLease(request: ResourceRequest): ResourceLease = when (val grant = coordinator.acquire(request)) {
        is ResourceGrant.Granted -> grant.lease
        is ResourceGrant.Refused -> error(grant.reason)
    }
    fun summary(): String {
        val state = coordinator.snapshot()
        return "Ressources (ce processus) : lourd=${state.heavy?.let { "${it.module}/${it.operation}" } ?: "aucun"} ; modèle=${state.model?.modelKey ?: "aucun"}. Enveloppes déclarées, pas RAM allouée."
    }
}
