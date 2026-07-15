package com.restaurantpos.core.database.repository

import com.restaurantpos.core.database.dao.DailySnapshotDao
import com.restaurantpos.core.database.entity.toDomain
import com.restaurantpos.core.database.entity.toEntity
import com.restaurantpos.core.domain.repository.ReportRepository
import com.restaurantpos.core.model.DailySnapshot
import com.restaurantpos.core.sync.SyncEntityType
import com.restaurantpos.core.sync.SyncOperation
import com.restaurantpos.core.sync.SyncWriter
import org.json.JSONObject

/**
 * ReportRepository 的 Room 实现
 * DAO 直接返回 DailySnapshot（本身即是 @Entity），无需映射
 * 保存时通过 SyncWriter 将快照推送到服务器
 */
class RoomReportRepository(
    private val dailySnapshotDao: DailySnapshotDao,
    private val syncWriter: SyncWriter? = null,
) : ReportRepository {

    override suspend fun getDailySnapshots(startDate: String, endDate: String): List<DailySnapshot> {
        return dailySnapshotDao.getInRange(startDate, endDate).map { it.toDomain() }
    }

    override suspend fun saveDailySnapshot(snapshot: DailySnapshot) {
        dailySnapshotDao.insertOrUpdate(snapshot.toEntity())
        syncWriter?.enqueue(
            entityType = SyncEntityType.DAILY_SNAPSHOT,
            entityId = snapshot.date,
            operation = SyncOperation.UPDATE,
            payload = snapshotToJson(snapshot),
        )
    }

    override suspend fun saveDailySnapshots(snapshots: List<DailySnapshot>) {
        dailySnapshotDao.insertOrUpdateAll(snapshots.map { it.toEntity() })
        snapshots.forEach { snapshot ->
            syncWriter?.enqueue(
                entityType = SyncEntityType.DAILY_SNAPSHOT,
                entityId = snapshot.date,
                operation = SyncOperation.UPDATE,
                payload = snapshotToJson(snapshot),
            )
        }
    }

    override suspend fun getDailySnapshot(date: String): DailySnapshot? {
        return dailySnapshotDao.getByDate(date)?.toDomain()
    }

    override suspend fun deleteSnapshots(startDate: String, endDate: String) {
        dailySnapshotDao.deleteInRange(startDate, endDate)
    }

    private fun snapshotToJson(s: DailySnapshot): String = JSONObject().apply {
        put("date", s.date)
        put("netRevenueMinorUnit", s.netRevenueMinorUnit)
        put("grossRevenueMinorUnit", s.grossRevenueMinorUnit)
        put("orderCount", s.orderCount)
        put("guestCount", s.guestCount)
        put("avgCheckMinorUnit", s.avgCheckMinorUnit)
        put("avgPerGuestMinorUnit", s.avgPerGuestMinorUnit)
        put("discountTotalMinorUnit", s.discountTotalMinorUnit)
        put("taxTotalMinorUnit", s.taxTotalMinorUnit)
        put("serviceChargeTotalMinorUnit", s.serviceChargeTotalMinorUnit)
        put("tipTotalMinorUnit", s.tipTotalMinorUnit)
        put("paymentBreakdownJson", s.paymentBreakdownJson)
        put("updatedAt", s.updatedAt)
    }.toString()
}
