package fr.jarvis.fixture.llm.f3

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Every snapshot below is SYNTHETIC. No JNI or native release evidence. */
class ObservationTest {
    private fun id() = UUID.randomUUID().toString()
    private val key = OperationKey(id(), id(), id(), id(), 3, id(), 7, id(), id(), id(), "a".repeat(64))
    private val e = Expectation(id(), id(), id(), key, 2, 9, id(), 4, 5)
    private fun sample() = Observation(e.process,e.worker,e.owner,key,2,9,e.barrier,0,0,0,0,0,
        Ownership.OWNED,Ownership.EMPTY,Ownership.EMPTY,4,5,Completion.UNLOADED)
    @Test fun missingPortNeverConstructsBackendOrClaimsRelease() = runBlocking {
        assertEquals(Completion.UNKNOWN, ObservationReview.review(e,UnavailableObservationPort.observe(e)).state)
    }
    @Test fun emptyModelMayKeepLibraryHandleWithoutClaimingRssZero() {
        val result = ObservationReview.review(e,sample())
        assertEquals(Completion.UNLOADED,result.state); assertEquals("CONSISTENT_CANDIDATE_ONLY",result.reason)
    }
    @Test fun eachMissingOrForeignProvenanceBlocksCandidate() {
        val a=sample()
        val mutations=listOf(a.copy(process=null),a.copy(process=id()),a.copy(worker=null),a.copy(worker=id()),
            a.copy(owner=null),a.copy(owner=id()),a.copy(key=null),a.copy(key=key.copy(lease=id())),
            a.copy(key=key.copy(generation=4)),a.copy(key=key.copy(leaseGeneration=8)),
            a.copy(key=key.copy(epoch=id())),a.copy(key=key.copy(session=id())),
            a.copy(key=key.copy(operation=id())),a.copy(key=key.copy(correlation=id())),
            a.copy(key=key.copy(cancellation=id())),a.copy(key=key.copy(authorization=id())),
            a.copy(key=key.copy(requestDigest="b".repeat(64))),
            a.copy(registryGeneration=null),a.copy(registryGeneration=3),a.copy(admissionRevision=null),
            a.copy(admissionRevision=10),a.copy(barrier=null),a.copy(barrier=id()))
        mutations.forEach { assertEquals(it.toString(),Completion.UNKNOWN,ObservationReview.review(e,it).state) }
    }
    @Test fun everyUnknownCounterAndOwnershipBlocksCandidate() {
        val a=sample()
        listOf(a.copy(queue=null),a.copy(imports=null),a.copy(calls=null),a.copy(modelLeases=null),
            a.copy(heavyLeases=null),a.copy(queue=-1),a.copy(handle=Ownership.UNKNOWN),
            a.copy(model=Ownership.UNKNOWN),a.copy(context=Ownership.UNKNOWN)).forEach {
            assertEquals(Completion.UNKNOWN,ObservationReview.review(e,it).state)
        }
    }
    @Test fun queuedImportCallLeaseAndSequencesCannotDisguiseDrainOrUnload() {
        val a=sample()
        listOf(a.copy(queue=1),a.copy(imports=1),a.copy(calls=1),a.copy(modelLeases=1),a.copy(heavyLeases=1),
            a.copy(callEndSequence=null),a.copy(callEndSequence=3),a.copy(unloadSequence=null),
            a.copy(unloadSequence=4),a.copy(model=Ownership.OWNED),a.copy(context=Ownership.OWNED),
            a.copy(completion=Completion.UNKNOWN)).forEach {
            assertEquals(Completion.UNKNOWN,ObservationReview.review(e,it).state)
        }
    }
    @Test fun stopDrainFailureAndUnloadRemainDifferent() {
        for(state in listOf(Completion.RUNNING,Completion.STOP_REQUESTED,Completion.DRAINED,Completion.CLEANUP_FAILED))
            assertEquals(state,ObservationReview.review(e,sample().copy(completion=state)).state)
    }
    @Test fun invalidExpectedSequencesNeverAuthorizeAnEmptySnapshot() {
        listOf(e.copy(minimumCallEnd=0),e.copy(minimumUnload=0),e.copy(registryGeneration=0),
            e.copy(admissionRevision=0),e.copy(owner="")).forEach {
            assertEquals(Completion.UNKNOWN,ObservationReview.review(it,sample()).state)
        }
    }
    @Test fun processRestartCannotConsumeOldObservationEvenWithSameOperation() {
        assertEquals(Completion.UNKNOWN,ObservationReview.review(e.copy(process=id()),sample()).state)
    }
    @Test fun keysRequireRealIdentityShapeAndPositiveGenerations() {
        for(f in listOf<()->Any>({key.copy(lease="NONE")},{key.copy(generation=0)},{key.copy(leaseGeneration=0)}))
            try { f(); fail() } catch(_:IllegalArgumentException) { }
    }
}
