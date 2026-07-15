package com.restaurantpos.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.restaurantpos.core.database.entity.ComboComponentEntity
import com.restaurantpos.core.database.entity.ComboEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ComboDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCombo(combo: ComboEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertComponents(components: List<ComboComponentEntity>)

    @Query("SELECT * FROM combos WHERE isActive = 1")
    fun observeActive(): Flow<List<ComboEntity>>

    @Query("SELECT * FROM combos WHERE id = :id")
    suspend fun getById(id: String): ComboEntity?

    @Query("SELECT * FROM combo_components WHERE comboId = :comboId ORDER BY sortOrder")
    suspend fun getComponents(comboId: String): List<ComboComponentEntity>

    @Transaction
    suspend fun upsertFull(combo: ComboEntity, components: List<ComboComponentEntity>) {
        upsertCombo(combo)
        upsertComponents(components)
    }
}
