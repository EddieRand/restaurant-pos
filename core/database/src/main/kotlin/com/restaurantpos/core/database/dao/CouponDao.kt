package com.restaurantpos.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.restaurantpos.core.database.entity.CouponEntity

@Dao
interface CouponDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(coupon: CouponEntity)

    @Query("SELECT * FROM coupons WHERE code = :code AND isActive = 1 LIMIT 1")
    suspend fun getByCode(code: String): CouponEntity?

    @Query("SELECT * FROM coupons WHERE id = :id")
    suspend fun getById(id: String): CouponEntity?
}
