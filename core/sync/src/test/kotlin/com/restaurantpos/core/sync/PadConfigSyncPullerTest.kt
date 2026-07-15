package com.restaurantpos.core.sync

import com.restaurantpos.core.config.AycePackage
import com.restaurantpos.core.config.ConfigRepository
import com.restaurantpos.core.config.DefaultRegionConfig
import com.restaurantpos.core.config.PadConfig
import com.restaurantpos.core.config.RegionConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Batch 45 — PAD config pull-sync unit tests (JVM, no Android dependency).
 *
 * Covers:
 * - Pull on start applies the merchant's PAD config into the local ConfigRepository
 * - Identical config does not trigger a redundant repository update
 * - Network/parse failure leaves the local config untouched (safe to retry)
 * - Other RegionConfig fields are preserved — only padConfig is replaced
 */
class PadConfigSyncPullerTest {

    private lateinit var port: FakePadConfigPullPort
    private lateinit var configRepository: FakeConfigRepository
    private lateinit var puller: PadConfigSyncPuller

    @Before
    fun setup() {
        port = FakePadConfigPullPort()
        configRepository = FakeConfigRepository(DefaultRegionConfig)
        puller = PadConfigSyncPuller(
            port = port,
            configRepository = configRepository,
            network = FakeNetworkMonitor(initialOnline = true),
        )
    }

    @Test
    fun `pull applies merchant pad config into local config repository`() = runBlocking {
        val remote = padConfig(idleTimeoutSeconds = 90, ayceEnabled = true)
        port.result = remote

        puller.pull()

        assertEquals(remote, configRepository.current().padConfig)
        assertEquals(1, configRepository.updateCount)
    }

    @Test
    fun `identical config does not trigger a redundant update`() = runBlocking {
        val same = configRepository.current().padConfig
        port.result = same

        puller.pull()

        assertEquals(0, configRepository.updateCount)
    }

    @Test
    fun `failed pull leaves local config untouched`() = runBlocking {
        port.result = null // simulates network/parse failure

        val before = configRepository.current()
        puller.pull()

        assertEquals(before, configRepository.current())
        assertEquals(0, configRepository.updateCount)
    }

    @Test
    fun `pull preserves other region config fields`() = runBlocking {
        val remote = padConfig(idleTimeoutSeconds = 45)
        port.result = remote

        puller.pull()

        val current = configRepository.current()
        assertEquals(DefaultRegionConfig.terminalId, current.terminalId)
        assertEquals(DefaultRegionConfig.currencyCode, current.currencyCode)
        assertEquals(remote, current.padConfig)
    }

    private fun padConfig(idleTimeoutSeconds: Int = 60, ayceEnabled: Boolean = false) = PadConfig(
        ayceEnabled = ayceEnabled,
        aycePackages = listOf(AycePackage(id = "pkg-1", name = "Standard", totalRoundsLimit = 3)),
        idleTimeoutSeconds = idleTimeoutSeconds,
        idlePromoLines = listOf("Welcome!"),
        supportedLocales = listOf("en", "zh"),
    )
}

class FakePadConfigPullPort : PadConfigPullPort {
    var result: PadConfig? = null
    override suspend fun pullPadConfig(): PadConfig? = result
}

class FakeConfigRepository(initial: RegionConfig) : ConfigRepository {
    private val _config = MutableStateFlow(initial)
    var updateCount = 0
        private set

    override val config: Flow<RegionConfig> = _config.asStateFlow()
    override fun current(): RegionConfig = _config.value
    override suspend fun update(config: RegionConfig) {
        updateCount++
        _config.value = config
    }
}
