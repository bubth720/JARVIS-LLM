package fr.jarvis.fixture.llm.f3

/** Isolated participation ledger. Activity tickets are NOT ProcessResources leases.
 * Cannot guard legacy admissions until ALL real entry points are instrumented (not done in F3A).
 * Never unfreezes itself after timeout, death, service recreation or drain.
 */
internal class AdmissionBarrier(private val process: String, private val expected: Set<String>) {
    enum class Work { IMPORT, LOAD, GENERATE, UNLOAD, CLOSE }
    data class Ticket(val process: String, val participant: String, val id: Long,
                                          val key: OperationKey, val work: Work)
    data class Frozen(val process: String, val round: String, val barrier: String, val revision: Long)
    data class Inventory(val revision: Long, val queued: Int, val imports: Int, val active: Int)
    private val registered = mutableSetOf<String>()
    private val tickets = mutableMapOf<Long, Pair<Ticket, Boolean>>()
    private var sealed = false
    private var revision = 0L
    private var next = 0L
    private var frozen: Frozen? = null
    private var failed = false
    init { require(process.isNotBlank() && expected.isNotEmpty() && expected.all { it.isNotBlank() }) }
    @Synchronized fun register(participant: String) {
        check(!sealed && participant in expected && registered.add(participant))
    }
    @Synchronized fun seal() { check(!sealed && registered == expected); sealed = true }
    @Synchronized fun admit(participant: String, key: OperationKey, work: Work): Ticket {
        check(sealed && !failed && frozen == null && participant in registered) { "ADMISSION_BLOCKED" }
        check(tickets.size < 128) { "QUEUE_FULL" }
        val ticket = Ticket(process, participant, ++next, key, work)
        tickets[ticket.id] = ticket to false; revision++
        return ticket
    }
    @Synchronized fun start(ticket: Ticket) {
        val entry = tickets[ticket.id]
        check(!failed && entry?.first === ticket && !entry.second) { "STALE_TICKET" }
        tickets[ticket.id] = ticket to true; revision++
    }
    /** Caller must know the work really returned. No coroutine-finally adapter is supplied. */
    @Synchronized fun returned(ticket: Ticket) {
        check(tickets[ticket.id]?.first === ticket && tickets[ticket.id]?.second == true) { "STALE_TICKET" }
        tickets.remove(ticket.id); revision++
    }
    @Synchronized fun freeze(round: String, barrier: String): Frozen {
        require(round.isNotBlank() && barrier.isNotBlank())
        check(sealed && !failed) { "INCOMPLETE_REGISTRY" }
        frozen?.let { check(it.round == round && it.barrier == barrier) { "BARRIER_CONFLICT" }; return it }
        return Frozen(process, round, barrier, ++revision).also { frozen = it }
    }
    @Synchronized fun inspect(token: Frozen): Inventory {
        check(!failed && frozen === token && token.process == process) { "UNKNOWN" }
        return Inventory(revision, tickets.values.count { !it.second },
            tickets.values.count { it.first.work == Work.IMPORT }, tickets.values.count { it.second })
    }
    @Synchronized fun quarantine() { failed = true } // uncertainty is irreversible in this candidate
    // Intentionally no resume/reset/release-native API in F3A.
}
