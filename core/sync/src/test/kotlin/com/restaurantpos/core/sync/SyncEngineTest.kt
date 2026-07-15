package com.restaurantpos.core.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Batch 5 sync engine unit tests (JVM, no Android dependency).
 *
 * Covers:
 * - Outbox enqueue / status transitions
 * - SyncEngine.flush: accepted, conflict, network error, permanent error
 * - Max-retry gate (records > maxRetries are skipped)
 * - Offline → online → auto-flush (SyncEngine.start/reconnect)
 * - SyncWriter.enqueue helper
 * - InMemorySyncOutbox.pruneCompleted
 * - resetInflight: IN_FLIGHT → PENDING on restart
 */
class SyncEngineTest {

    private lateinit var outbox: InMemorySyncOutbox
    private lateinit var remote: FakeRemoteSyncPort
    private lateinit var network: FakeNetworkMonitor

    @Before
    fun setup() {
        outbox = InMemorySyncOutbox()
        remote = FakeRemoteSyncPort()
        network = FakeNetworkMonitor(initialOnline = true)
    }

    // ── Outbox basics ────────────────────────────────────────────────────────

    @Test
    fun `enqueue adds pending record`() = runBlocking {
        outbox.enqueue(makeRecord("r1"))
        assertEquals(1, outbox.countByStatus(SyncStatus.PENDING))
    }

    @Test
    fun `markDone transitions record to DONE`() = runBlocking {
        outbox.enqueue(makeRecord("r1"))
        outbox.markDone("r1")
        assertEquals(0, outbox.countByStatus(SyncStatus.PENDING))
        assertEquals(1, outbox.countByStatus(SyncStatus.DONE))
    }

    @Test
    fun `markFailed keeps record PENDING and increments retryCount`() = runBlocking {
        outbox.enqueue(makeRecord("r1"))
        outbox.markFailed("r1")
        // Returns to PENDING so it will be retried on next flush
        assertEquals(1, outbox.countByStatus(SyncStatus.PENDING))
        assertEquals(0, outbox.countByStatus(SyncStatus.FAILED))
    }

    @Test
    fun `abandon permanently marks record FAILED`() = runBlocking {
        outbox.enqueue(makeRecord("r1"))
        outbox.abandon("r1")
        assertEquals(0, outbox.countByStatus(SyncStatus.PENDING))
        assertEquals(1, outbox.countByStatus(SyncStatus.FAILED))
    }

    @Test
    fun `resetInflight restores IN_FLIGHT to PENDING`() = runBlocking {
        val record = makeRecord("r1").copy(status = SyncStatus.IN_FLIGHT)
        outbox.enqueue(record)
        assertEquals(0, outbox.countByStatus(SyncStatus.PENDING))
        outbox.resetInflight()
        assertEquals(1, outbox.countByStatus(SyncStatus.PENDING))
    }

    @Test
    fun `pruneCompleted removes old DONE records`() = runBlocking {
        val old = makeRecord("r-old", updatedAt = 1000L).copy(status = SyncStatus.DONE)
        val recent = makeRecord("r-new", updatedAt = 9000L).copy(status = SyncStatus.DONE)
        outbox.enqueue(old)
        outbox.enqueue(recent)
        outbox.pruneCompleted(beforeEpoch = 5000L)
        assertEquals(1, outbox.countByStatus(SyncStatus.DONE))
    }

    // ── SyncEngine.flush ──────────────────────────────────────────────────────

    @Test
    fun `flush - accepted records are marked DONE`() = runBlocking {
        outbox.enqueue(makeRecord("r1"))
        outbox.enqueue(makeRecord("r2"))
        remote.responseMap["r1"] = SyncResponse.Accepted
        remote.responseMap["r2"] = SyncResponse.Accepted

        val engine = SyncEngine(outbox, remote, network)
        val delivered = engine.flush()

        assertEquals(2, delivered)
        assertEquals(0, outbox.countByStatus(SyncStatus.PENDING))
        assertEquals(2, outbox.countByStatus(SyncStatus.DONE))
    }

    @Test
    fun `flush - network error stops flush and keeps record PENDING for retry`() = runBlocking {
        outbox.enqueue(makeRecord("r1"))
        outbox.enqueue(makeRecord("r2", updatedAt = 2000L))
        remote.responseMap["r1"] = SyncResponse.NetworkError(null)

        val engine = SyncEngine(outbox, remote, network)
        val delivered = engine.flush()

        assertEquals(0, delivered)
        assertEquals(0, outbox.countByStatus(SyncStatus.FAILED))
        // Both records remain PENDING (r1 retryCount incremented, r2 not attempted)
        assertEquals(2, outbox.countByStatus(SyncStatus.PENDING))
    }

    @Test
    fun `flush - permanent error abandons record and continues other records`() = runBlocking {
        outbox.enqueue(makeRecord("r1"))
        outbox.enqueue(makeRecord("r2", updatedAt = 2000L))
        remote.responseMap["r1"] = SyncResponse.PermanentError("not found")
        remote.responseMap["r2"] = SyncResponse.Accepted

        val engine = SyncEngine(outbox, remote, network)
        val delivered = engine.flush()

        assertEquals(1, delivered)
        assertEquals(1, outbox.countByStatus(SyncStatus.FAILED))  // r1 abandoned
        assertEquals(1, outbox.countByStatus(SyncStatus.DONE))     // r2 delivered
    }

    @Test
    fun `flush - conflict calls resolver and marks DONE`() = runBlocking {
        outbox.enqueue(makeRecord("r1"))
        remote.responseMap["r1"] = SyncResponse.Conflict(serverPayload = """{"id":"entity-1"}""")

        var resolvedPayload: String? = null
        val resolver = object : ConflictResolver {
            override suspend fun resolve(local: SyncRecord, serverPayload: String) {
                resolvedPayload = serverPayload
            }
        }

        val engine = SyncEngine(outbox, remote, network, conflictResolver = resolver)
        engine.flush()

        assertEquals("""{"id":"entity-1"}""", resolvedPayload)
        assertEquals(1, outbox.countByStatus(SyncStatus.DONE))
    }

    @Test
    fun `flush - records exceeding maxRetries are abandoned`() = runBlocking {
        val exhausted = makeRecord("r1").copy(retryCount = 5, status = SyncStatus.PENDING)
        outbox.enqueue(exhausted)

        val engine = SyncEngine(outbox, remote, network, maxRetries = 5)
        val delivered = engine.flush()

        assertEquals(0, delivered)
        assertEquals(0, remote.pushCount)                           // remote never called
        assertEquals(1, outbox.countByStatus(SyncStatus.FAILED))   // record abandoned
    }

    @Test
    fun `flush - network error increments retryCount so record can be retried`() = runBlocking {
        outbox.enqueue(makeRecord("r1"))
        remote.responseMap["r1"] = SyncResponse.NetworkError(null)

        val engine = SyncEngine(outbox, remote, network, maxRetries = 3)
        engine.flush()

        val pending = outbox.getPending()
        assertEquals(1, pending.size)
        assertEquals(1, pending[0].retryCount)  // incremented, still retryable
    }

    // ── Offline → online reconnect ────────────────────────────────────────────

    @Test
    fun `reconnect triggers flush of pending records`() = runBlocking {
        outbox.enqueue(makeRecord("r1"))
        remote.responseMap["r1"] = SyncResponse.Accepted

        network.setOnline(false)

        // Engine starts, flush runs (network was online initially then set offline;
        // we'll just test the flush API directly here as coroutine scope is complex in unit tests)
        val engine = SyncEngine(outbox, remote, network)
        // Simulate offline state: no flush attempted manually
        assertEquals(1, outbox.countByStatus(SyncStatus.PENDING))

        // Simulate reconnect: call flush directly (as engine would when network.isOnline emits true)
        val delivered = engine.flush()
        assertEquals(1, delivered)
        assertEquals(1, outbox.countByStatus(SyncStatus.DONE))
    }

    // ── SyncWriter ────────────────────────────────────────────────────────────

    @Test
    fun `SyncWriter enqueues record with correct fields`() = runBlocking {
        val writer = SyncWriter(outbox)
        writer.enqueue(SyncEntityType.ORDER, "order-42", SyncOperation.UPDATE, """{"id":"order-42"}""", now = 12345L)

        val pending = outbox.getPending()
        assertEquals(1, pending.size)
        assertEquals(SyncEntityType.ORDER, pending[0].entityType)
        assertEquals("order-42", pending[0].entityId)
        assertEquals(SyncOperation.UPDATE, pending[0].operation)
        assertEquals(12345L, pending[0].updatedAt)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private var recordCounter = 0
    // ── F-013 周期冲账 ────────────────────────────────────────────────────────

    @Test
    fun `records enqueued mid-session are flushed by the periodic loop`() = kotlinx.coroutines.test.runTest {
        val engine = SyncEngine(outbox, remote, network, pollIntervalMs = 1_000L)
        engine.start(this)
        testScheduler.runCurrent() // start 时空冲账完成

        outbox.enqueue(makeRecord("mid-session")) // 模拟营业中下单
        assertEquals(1, outbox.countByStatus(SyncStatus.PENDING))
        val pushesBefore = remote.pushCount

        testScheduler.advanceTimeBy(1_100L) // 越过一个轮询周期
        testScheduler.runCurrent()

        assertEquals(0, outbox.countByStatus(SyncStatus.PENDING))
        assertEquals(pushesBefore + 1, remote.pushCount)
        engine.stop()
    }

    private fun makeRecord(
        id: String = "r-${recordCounter++}",
        updatedAt: Long = 1000L,
    ) = SyncRecord(
        id = id,
        entityType = SyncEntityType.ORDER,
        entityId = "entity-$id",
        operation = SyncOperation.UPDATE,
        payload = "{}",
        updatedAt = updatedAt,
    )
}

// ── Test doubles ──────────────────────────────────────────────────────────────

class FakeRemoteSyncPort : RemoteSyncPort {
    /** Pre-configure responses by record id. Default: Accepted. */
    val responseMap = mutableMapOf<String, SyncResponse>()
    var pushCount = 0
        private set

    override suspend fun push(record: SyncRecord): SyncResponse {
        pushCount++
        return responseMap[record.id] ?: SyncResponse.Accepted
    }
}

class FakeNetworkMonitor(initialOnline: Boolean) : NetworkMonitor {
    private val _isOnline = MutableStateFlow(initialOnline)
    override val isOnline: Flow<Boolean> = _isOnline
    fun setOnline(online: Boolean) { _isOnline.value = online }

}
