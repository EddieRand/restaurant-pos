package com.restaurantpos.core.sync

import com.restaurantpos.core.model.MenuItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Batch 44 — menu pull-sync unit tests (JVM, no Android dependency).
 *
 * Covers:
 * - First pull starts from watermark 0 and applies returned items
 * - Watermark advances to the server's reported time, not the client's
 * - Empty result still advances the watermark (no spurious re-fetch)
 * - Network/parse failure leaves the watermark untouched (safe to retry)
 */
class MenuSyncPullerTest {

    private lateinit var port: FakeMenuPullPort
    private lateinit var watermark: InMemorySyncWatermarkStore
    private lateinit var applied: MutableList<List<MenuItem>>
    private lateinit var puller: MenuSyncPuller

    @Before
    fun setup() {
        port = FakeMenuPullPort()
        watermark = InMemorySyncWatermarkStore()
        applied = mutableListOf()
        puller = MenuSyncPuller(
            port = port,
            watermarkStore = watermark,
            network = FakeNetworkMonitor(initialOnline = true),
            applyItems = { items -> applied.add(items) },
        )
    }

    @Test
    fun `first pull queries from watermark zero and applies items`() = runBlocking {
        port.result = MenuPullResult(serverTime = 1_000L, items = listOf(item("a")))

        puller.pull()

        assertEquals(0L, port.lastSinceQueried)
        assertEquals(1, applied.size)
        assertEquals(listOf("a"), applied.first().map { it.id })
    }

    @Test
    fun `watermark advances to server time after successful pull`() = runBlocking {
        port.result = MenuPullResult(serverTime = 5_000L, items = listOf(item("a")))

        puller.pull()

        assertEquals(5_000L, watermark.getLastPullAt(SyncEntityType.MENU_ITEM))
    }

    @Test
    fun `subsequent pull uses the stored watermark as cursor`() = runBlocking {
        port.result = MenuPullResult(serverTime = 5_000L, items = listOf(item("a")))
        puller.pull()

        port.result = MenuPullResult(serverTime = 9_000L, items = emptyList())
        puller.pull()

        assertEquals(5_000L, port.lastSinceQueried)
        assertEquals(9_000L, watermark.getLastPullAt(SyncEntityType.MENU_ITEM))
    }

    @Test
    fun `empty result still advances watermark without invoking apply`() = runBlocking {
        port.result = MenuPullResult(serverTime = 3_000L, items = emptyList())

        puller.pull()

        assertEquals(0, applied.size)
        assertEquals(3_000L, watermark.getLastPullAt(SyncEntityType.MENU_ITEM))
    }

    @Test
    fun `failed pull leaves watermark untouched`() = runBlocking {
        port.result = null // simulates network/parse failure

        puller.pull()

        assertEquals(0, applied.size)
        assertEquals(0L, watermark.getLastPullAt(SyncEntityType.MENU_ITEM))
    }

    @Test
    fun `menu changes published mid-session arrive via the periodic poll`() = kotlinx.coroutines.test.runTest {
        port.result = MenuPullResult(serverTime = 1_000L, items = emptyList())
        val polling = MenuSyncPuller(
            port = port,
            watermarkStore = watermark,
            network = FakeNetworkMonitor(initialOnline = true),
            applyItems = { items -> applied.add(items) },
            pollIntervalMs = 1_000L,
        )
        polling.start(this)
        testScheduler.runCurrent() // initial pull (empty)
        assertEquals(0, applied.size)

        port.result = MenuPullResult(serverTime = 2_000L, items = listOf(item("late"))) // admin edit mid-session
        testScheduler.advanceTimeBy(1_100L)
        testScheduler.runCurrent()

        assertEquals(1, applied.size)
        assertEquals(listOf("late"), applied.first().map { it.id })
        polling.stop()
    }

    private fun item(id: String) = MenuItem(
        id = id,
        names = mapOf("en" to id),
        priceMinorUnit = 100L,
        taxRateId = null,
        categoryId = "cat-1",
    )
}

class FakeMenuPullPort : MenuPullPort {
    var result: MenuPullResult? = null
    var lastSinceQueried: Long = -1L
        private set

    override suspend fun pullMenuItems(since: Long): MenuPullResult? {
        lastSinceQueried = since
        return result
    }
}

class InMemorySyncWatermarkStore : SyncWatermarkStore {
    private val values = mutableMapOf<SyncEntityType, Long>()
    override suspend fun getLastPullAt(entityType: SyncEntityType): Long = values[entityType] ?: 0L
    override suspend fun setLastPullAt(entityType: SyncEntityType, timestamp: Long) {
        values[entityType] = timestamp
    }
}
