package com.restaurantpos.core.domain.repository

import com.restaurantpos.core.model.DailySnapshot

/**
 * 报表数据仓库接口 - 用于时序数据访问
 */
interface ReportRepository {
    /**
     * 获取指定日期范围的日快照数据
     * 
     * @param startDate 开始日期 (yyyy-MM-dd)
     * @param endDate 结束日期 (yyyy-MM-dd)
     * @return 日快照列表
     */
    suspend fun getDailySnapshots(startDate: String, endDate: String): List<DailySnapshot>
    
    /**
     * 保存或更新日快照
     */
    suspend fun saveDailySnapshot(snapshot: DailySnapshot)
    
    /**
     * 批量保存日快照
     */
    suspend fun saveDailySnapshots(snapshots: List<DailySnapshot>)
    
    /**
     * 获取指定日期的快照，如果不存在则返回 null
     */
    suspend fun getDailySnapshot(date: String): DailySnapshot?
    
    /**
     * 删除指定日期范围的历史快照（用于数据清理）
     */
    suspend fun deleteSnapshots(startDate: String, endDate: String)
}
