package com.restaurantpos.app.cashier.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.restaurantpos.core.domain.usecase.DailyReportUseCase
import com.restaurantpos.core.config.ConfigRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.ZoneId

/**
 * WorkManager 定时任务：每日凌晨自动生成前一天的 DailySnapshot。
 *
 * 调度方式：在 CashierApplication.onCreate() 中通过
 * PeriodicWorkRequestBuilder<DailySnapshotWorker>(24, TimeUnit.HOURS)
 * 注册每日执行。
 *
 * 触发时：生成昨天的快照并保存到 Room DB，确保次日"昨日对比"
 * 数据可用，无需实时计算。
 */
@HiltWorker
class DailySnapshotWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dailyReportUseCase: DailyReportUseCase,
    private val configRepo: ConfigRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val zone = runCatching { ZoneId.of(configRepo.current().timeZone) }
                .getOrElse { ZoneId.of("UTC") }
            val yesterday = LocalDate.now(zone).minusDays(1).toString()

            // Generate yesterday's snapshot (will be saved via reportRepo)
            dailyReportUseCase.generateDailySnapshot(yesterday)

            Result.success()
        } catch (e: Exception) {
            // Retry up to 3 times before giving up
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
