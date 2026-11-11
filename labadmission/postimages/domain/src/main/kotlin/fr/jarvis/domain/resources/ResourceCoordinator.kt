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
    private val released = java.util.concurrent.atomic.AtomicBoolean(false)
    // Never invoke external revocation callbacks under a lease monitor.
    // CAS seals ownership even when a callback fails; a second close cannot reissue it.
    override fun close() { if (released.compareAndSet(false,true)) release() }
    override fun toString() = "ResourceLease(redacted)"
}
/** Minted by the actual broker; callers cannot manufacture or transfer ownership with these fields. */
class ResourceObservation private constructor(private val broker: ResourceCoordinator,
    private val heavyLease: ResourceLease, private val modelLease: ResourceLease,
    val brokerIncarnation: String, val revision: Long, val heavyIdentity: Long, val modelIdentity: Long) : AutoCloseable {
    @Volatile private var closed = false
    fun isLive(): Boolean = !closed && broker.isLive(this, heavyLease, modelLease)
    fun onRevoked(action: () -> Unit) = broker.watch(this, action)
    @Synchronized override fun close() { if (!closed) { closed = true; broker.unpin(this, heavyLease, modelLease) } }
    override fun toString() = "ResourceObservation(redacted)"
    companion object {
        internal fun mint(b: ResourceCoordinator, h: ResourceLease, m: ResourceLease,
                          incarnation: String, revision: Long, hid: Long, mid: Long) =
            ResourceObservation(b,h,m,incarnation,revision,hid,mid)
    }
}
class ResourceCoordinator {
    private data class Entry(val id: Long, val request: ResourceRequest, val lease: ResourceLease,
                             var pins: Int = 0, var revoked: Boolean = false)
    private val incarnation = java.util.UUID.randomUUID().toString()
    private var nextId = 0L
    private var revision = 0L
    private var heavy: Entry? = null
    private var model: Entry? = null
    private val observations = java.util.IdentityHashMap<ResourceObservation, (() -> Unit)?>()
    @Synchronized fun acquire(request: ResourceRequest): ResourceGrant {
        val reason = when (request.kind) {
            ResourceClass.HEAVY -> when {
                heavy != null -> "Traitement lourd déjà actif : ${heavy!!.request.module}/${heavy!!.request.operation}"
                model != null && model!!.request.owner != request.owner -> "Libérer le modèle résident avant un traitement lourd d'un autre propriétaire"
                else -> null
            }
            ResourceClass.MODEL -> when {
                model != null -> "Un modèle est déjà chargé ou en cours de chargement"
                heavy?.request?.owner != request.owner || heavy!!.revoked -> "Le chargement exige un accès lourd exclusif du même propriétaire"
                else -> null
            }
        }
        if (reason != null) return ResourceGrant.Refused(reason)
        check(nextId < Long.MAX_VALUE && revision < Long.MAX_VALUE) { "BROKER_SEQUENCE_EXHAUSTED" }
        val id = ++nextId
        val lease = ResourceLease { release(request.kind, id) }
        val entry = Entry(id, request, lease)
        if (request.kind == ResourceClass.HEAVY) heavy = entry else model = entry
        revision++
        return ResourceGrant.Granted(lease)
    }
    private fun release(kind: ResourceClass, id: Long) {
        val notify = synchronized(this) {
            val e = (if (kind == ResourceClass.HEAVY) heavy else model)?.takeIf { it.id == id } ?: return
            if (e.revoked) return
            check(revision < Long.MAX_VALUE); e.revoked = true; revision++
            if (e.pins == 0) { if (kind == ResourceClass.HEAVY) heavy = null else model = null }
            observations.values.filterNotNull().toList()
        }
        // Native cancel never runs under the broker monitor; slots stay pinned until actual return.
        // Deliver every revocation even when one observer throws. Keep the first failure
        // visible to the caller, and attach later failures without masking the first.
        var firstFailure: Throwable? = null
        notify.forEach { callback ->
            try { callback() } catch (failure: Throwable) {
                if (firstFailure == null) firstFailure = failure
                else if (firstFailure !== failure) firstFailure!!.addSuppressed(failure)
            }
        }
        firstFailure?.let { throw it }
    }
    internal fun watch(p: ResourceObservation, action: () -> Unit) {
        val stale = synchronized(this) {
            check(observations.containsKey(p));check(observations[p] == null)
            observations[p] = action
            p.revision != revision
        }
        if (stale) action()
    }
    /** Identity checks and both pins are atomic. No external callback runs under this monitor. */
    @Synchronized fun observeOwnedPair(owner: String, heavyLease: ResourceLease, modelLease: ResourceLease): ResourceObservation? {
        val h = heavy ?: return null; val m = model ?: return null
        if (h.lease !== heavyLease || m.lease !== modelLease || h.revoked || m.revoked ||
            h.request.owner != owner || m.request.owner != owner ||
            h.request.module != Module.LLM || m.request.module != Module.LLM) return null
        val proof = ResourceObservation.mint(this,h.lease,m.lease,incarnation,revision,h.id,m.id)
        h.pins++; m.pins++; observations[proof] = null
        return proof
    }
    @Synchronized internal fun isLive(p: ResourceObservation, h: ResourceLease, m: ResourceLease): Boolean =
        observations.containsKey(p) && p.brokerIncarnation == incarnation && p.revision == revision &&
        heavy?.let { it.lease === h && it.id == p.heavyIdentity && !it.revoked } == true &&
        model?.let { it.lease === m && it.id == p.modelIdentity && !it.revoked } == true
    @Synchronized internal fun unpin(p: ResourceObservation, h: ResourceLease, m: ResourceLease) {
        if (!observations.containsKey(p)) return
        observations.remove(p)
        check(heavy?.lease === h && model?.lease === m)
        heavy!!.pins--; model!!.pins--
        if (heavy!!.revoked && heavy!!.pins == 0) heavy = null
        if (model!!.revoked && model!!.pins == 0) model = null
    }
    @Synchronized fun snapshot() = ResourceState(heavy?.request, model?.request)
}
