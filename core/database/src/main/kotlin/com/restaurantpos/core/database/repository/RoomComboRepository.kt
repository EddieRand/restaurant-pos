package com.restaurantpos.core.database.repository

import com.restaurantpos.core.database.dao.ComboDao
import com.restaurantpos.core.database.entity.ComboComponentEntity
import com.restaurantpos.core.database.entity.ComboEntity
import com.restaurantpos.core.database.entity.toDomain
import com.restaurantpos.core.domain.repository.ComboRepository
import com.restaurantpos.core.model.Combo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class RoomComboRepository(private val dao: ComboDao) : ComboRepository {

    override fun observeActive(): Flow<List<Combo>> =
        dao.observeActive().map { entities ->
            entities.map { entity ->
                val components = dao.getComponents(entity.id)
                entity.toDomain(components)
            }
        }

    override suspend fun getById(id: String): Combo? {
        val entity = dao.getById(id) ?: return null
        val components = dao.getComponents(id)
        return entity.toDomain(components)
    }

    override suspend fun save(combo: Combo) {
        val entity = ComboEntity(
            id = combo.id,
            names = combo.names,
            comboPriceMinorUnit = combo.comboPriceMinorUnit,
            taxRateId = combo.taxRateId,
        )
        val components = combo.components.mapIndexed { idx, c ->
            ComboComponentEntity(
                id = UUID.randomUUID().toString(),
                comboId = combo.id,
                menuItemId = c.menuItemId,
                quantity = c.quantity,
                sortOrder = idx,
            )
        }
        dao.upsertFull(entity, components)
    }
}
