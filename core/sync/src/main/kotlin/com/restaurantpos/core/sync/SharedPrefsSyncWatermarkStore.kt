package com.restaurantpos.core.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedPrefsSyncWatermarkStore @Inject constructor(
    @ApplicationContext context: Context,
) : SyncWatermarkStore {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("pos_sync_watermarks", Context.MODE_PRIVATE)

    override suspend fun getLastPullAt(entityType: SyncEntityType): Long =
        prefs.getLong(key(entityType), 0L)

    override suspend fun setLastPullAt(entityType: SyncEntityType, timestamp: Long) =
        prefs.edit { putLong(key(entityType), timestamp) }

    private fun key(entityType: SyncEntityType) = "last_pull_${entityType.name}"
}
