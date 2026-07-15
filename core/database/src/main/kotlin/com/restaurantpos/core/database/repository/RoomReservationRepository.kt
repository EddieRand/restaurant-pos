package com.restaurantpos.core.database.repository

import com.restaurantpos.core.database.dao.ReservationDao
import com.restaurantpos.core.database.entity.ReservationEntity
import com.restaurantpos.core.domain.repository.ReservationRepository
import com.restaurantpos.core.model.Reservation
import com.restaurantpos.core.model.ReservationStatus
import com.restaurantpos.core.sync.SyncEntityType
import com.restaurantpos.core.sync.SyncOperation
import com.restaurantpos.core.sync.SyncWriter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar

class RoomReservationRepository(
    private val dao: ReservationDao,
    private val syncWriter: SyncWriter,
) : ReservationRepository {

    override fun observeByDate(dateEpochMillis: Long): Flow<List<Reservation>> {
        val cal = Calendar.getInstance().apply { timeInMillis = dateEpochMillis }
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        val startOfDay = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
        val endOfDay = cal.timeInMillis
        return dao.observeByDate(startOfDay, endOfDay).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getById(id: String): Reservation? =
        dao.getById(id)?.toDomain()

    override suspend fun save(reservation: Reservation) {
        dao.upsert(ReservationEntity.fromDomain(reservation))
        syncWriter.enqueue(
            entityType = SyncEntityType.RESERVATION,
            entityId = reservation.id,
            operation = SyncOperation.CREATE,
            payload = """{"id":"${reservation.id}","tableId":"${reservation.tableId}","guestName":"${reservation.guestName}","scheduledAt":${reservation.scheduledAt}}""",
        )
    }

    override suspend fun updateStatus(id: String, status: ReservationStatus) {
        dao.updateStatus(id, status.name)
        syncWriter.enqueue(
            entityType = SyncEntityType.RESERVATION,
            entityId = id,
            operation = SyncOperation.UPDATE,
            payload = """{"id":"$id","status":"$status"}""",
        )
    }
}
