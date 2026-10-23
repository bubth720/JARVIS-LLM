package fr.jarvis.llm.runtime

import fr.jarvis.domain.runtime.RuntimeBackendPort
import fr.jarvis.domain.runtime.RuntimeResourceProjection
import fr.jarvis.llm.ProcessResources

/** Reads the already shared broker. Never constructs a coordinator or touches the native backend. */
class ProcessRuntimeObservationPort : RuntimeBackendPort {
    override fun observe() = RuntimeResourceProjection.project(ProcessResources.coordinator.snapshot())
}
