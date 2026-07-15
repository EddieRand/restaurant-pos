package com.restaurantpos.core.sync

import com.restaurantpos.core.config.ConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Pulls the server-authoritative PAD configuration (configured via Web Admin) down
 * into the local [ConfigRepository].
 *
 * Unlike [MenuSyncPuller], there is no incremental watermark — the config is a single
 * small blob, so every pull simply replaces [com.restaurantpos.core.config.RegionConfig.padConfig]
 * wholesale on [start] and on every reconnect.
 */
class PadConfigSyncPuller(
    private val port: PadConfigPullPort,
    private val configRepository: ConfigRepository,
    private val network: NetworkMonitor,
    private val pollIntervalMs: Long = 30_000L,
) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job = scope.launch {
            pull()
            launch {
                network.isOnline
                    .distinctUntilChanged()
                    .filter { it }
                    .collect { pull() }
            }
            // Poll periodically — admin config edits must reach devices mid-session
            while (isActive) {
                delay(pollIntervalMs)
                pull()
            }
        }
    }

    fun stop() { job?.cancel(); job = null }

    /** Fetch and apply the merchant's PAD config. Best-effort: swallows network errors. */
    suspend fun pull() {
        val padConfig = runCatching { port.pullPadConfig() }.getOrNull() ?: return
        val current = configRepository.current()
        if (current.padConfig != padConfig) {
            configRepository.update(current.copy(padConfig = padConfig))
        }
    }
}
