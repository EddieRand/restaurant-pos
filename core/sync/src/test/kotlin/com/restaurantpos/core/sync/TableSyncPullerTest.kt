package com.restaurantpos.core.sync

import com.restaurantpos.core.model.Table
import com.restaurantpos.core.model.TableStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** F-024 table pull-sync unit tests (JVM). */
class TableSyncPullerTest {

    private lateinit var port: FakeTablePullPort
    private lateinit var watermark: InMemorySyncWatermarkStore
    private lateinit var applied: MutableList<List<Table>>
    private lateinit var puller: TableSyncPuller

    @Before
    fun setup() {
        port = FakeTablePullPort()
        watermark = InMemorySyncWatermarkStore()
        applied = mutableListOf()
        puller = TableSyncPuller(
            port = port,
            watermarkStore = watermark,
            network = FakeNetworkMonitor(initialOnline = true),
            applyTables = { applied.add(it) },
        )
    }

    private fun table(id: String, status: TableStatus = TableStatus.AVAILABLE, updatedAt: Long = 1L) =
        Table(id = id, name = id, sectionId = "s", capacity = 4, status = status, updatedAt = updatedAt)

    @Test
    fun `first pull queries from watermark zero and applies tables`() = runBlocking {
        port.result = TablePullResult(serverTime = 1_000L, tables = listOf(table("T1")))
        puller.pull()
        assertEquals(0L, port.lastSince)
        assertEquals(1, applied.size)
        assertEquals(listOf("T1"), applied.first().map { it.id })
    }

    @Test
    fun `watermark advances to server time`() = runBlocking {
        port.result = TablePullResult(serverTime = 7_000L, tables = listOf(table("T1")))
        puller.pull()
        assertEquals(7_000L, watermark.getLastPullAt(SyncEntityType.TABLE))
    }

    @Test
    fun `subsequent pull uses stored watermark`() = runBlocking {
        port.result = TablePullResult(serverTime = 5_000L, tables = listOf(table("T1")))
        puller.pull()
        port.result = TablePullResult(serverTime = 9_000L, tables = emptyList())
        puller.pull()
        assertEquals(5_000L, port.lastSince)
    }

    @Test
    fun `failed pull leaves watermark untouched`() = runBlocking {
        port.result = null
        puller.pull()
        assertEquals(0L, watermark.getLastPullAt(SyncEntityType.TABLE))
        assertTrue(applied.isEmpty())
    }
}

class FakeTablePullPort : TablePullPort {
    var result: TablePullResult? = null
    var lastSince: Long = -1L
        private set

    override suspend fun pullTables(since: Long): TablePullResult? {
        lastSince = since
        return result
    }
}
