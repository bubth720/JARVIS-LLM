package fr.jarvis.llm.client

import fr.jarvis.domain.client.ClientCapabilities
import fr.jarvis.domain.client.NativeResourceState
import fr.jarvis.domain.runtime.RuntimeBackendPort
import fr.jarvis.llm.model.VerifiedModelCatalog
import fr.jarvis.llm.runtime.ProcessRuntimeObservationPort

enum class CatalogueSelectionState { NONE, CATALOGUE_SELECTED_NOT_RUNNING, INCONSISTENT }
class ClientReadiness(val selection: CatalogueSelectionState, val resources: NativeResourceState) {
    val capabilities = ClientCapabilities()
    val fileRevalidatedNow: Boolean get() = false
    override fun toString() = "ClientReadiness(selection=$selection,nativeReady=false)"
}
/** Borrow the ONE existing catalogue. No DB ownership, import, model path or engine construction.
 * Selection/provenance at import cannot prove current file integrity, residency or readiness. */
class LocalClientReadinessPort(private val catalogue: VerifiedModelCatalog,
                               private val runtime: RuntimeBackendPort = ProcessRuntimeObservationPort()) {
    fun observe(): ClientReadiness {
        val state = synchronized(catalogue) {
            val selected = catalogue.selected()
            when {
                selected == null -> CatalogueSelectionState.NONE
                catalogue.list().any { it.sha256 == selected } -> CatalogueSelectionState.CATALOGUE_SELECTED_NOT_RUNNING
                else -> CatalogueSelectionState.INCONSISTENT
            }
        }
        return ClientReadiness(state, NativeResourceState(runtime.observe()))
    }
}
