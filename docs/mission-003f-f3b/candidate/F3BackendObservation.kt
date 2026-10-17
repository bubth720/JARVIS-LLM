package fr.jarvis.llm.engine

import fr.jarvis.domain.resources.ResourceObservation

/** Raw process-local read, never an ATTEST/terminal receipt or grant.
 * The caller must freeze the full participant registry before interpreting it.
 */
data class F3BackendObservation(val trace: F3Trace.Snapshot, val resources: ResourceObservation,
    val coherent: Boolean)
