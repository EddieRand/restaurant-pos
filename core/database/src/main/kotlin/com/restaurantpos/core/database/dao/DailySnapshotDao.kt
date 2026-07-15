package com.restaurantpos.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.restaurantpos.core.database.entity.DailySnapshotEntity
import kotlinx.coroutines.flow.Flow

/**
 * 日快照 DAO - 用于时序数据访问
 * 返回 Room Entity，映射工作在 Repository 层完成
 */
@Dao
interface DailySnapshotDao {
    
    /**
     * 获取指定日期范围的日快照
     */
    @Query("SELECT * FROM daily_snapshots WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC")
    suspend fun getInRange(startDate: String, endDate: String): List<DailySnapshotEntity>
    
    /**
     * 获取指定日期范围的日快照（Flow 版本，用于实时观察）
     */
    @Query("SELECT * FROM daily_snapshots WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC")
    fun observeInRange(startDate: String, endDate: String): Flow<List<DailySnapshotEntity>>
    
    /**
     * 获取指定日期的快照
     */
    @Query("SELECT * FROM daily_snapshots WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): DailySnapshotEntity?
    
    /**
     * 插入或更新日快照
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(snapshot: DailySnapshotEntity)
    
    /**
     * 批量插入或更新日快照
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(snapshots: List<DailySnapshotEntity>)
    
    /**
     * 删除指定日期的快照
     */
    @Query("DELETE FROM daily_snapshots WHERE date = :date")
    suspend fun deleteByDate(date: String)
    
    /**
     * 删除指定日期范围的快照
     */
    @Query("DELETE FROM daily_snapshots WHERE date >= :startDate AND date <= :endDate")
    suspend fun deleteInRange(startDate: String, endDate: String)
    
    /**
     * 获取最新的快照日期
     */
    @Query("SELECT MAX(date) FROM daily_snapshots")
    suspend fun getLatestDate(): String?
    
    /**
     * 获取最旧的快照日期
     */
    @Query("SELECT MIN(date) FROM daily_snapshots")
    suspend fun getOldestDate(): String?
}
