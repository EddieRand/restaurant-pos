package com.restaurantpos.core.database.repository

import com.restaurantpos.core.database.dao.CouponDao
import com.restaurantpos.core.database.entity.CouponEntity
import com.restaurantpos.core.domain.repository.CouponRepository
import com.restaurantpos.core.model.Coupon

class RoomCouponRepository(private val dao: CouponDao) : CouponRepository {
    override suspend fun getByCode(code: String): Coupon? = dao.getByCode(code)?.toDomain()
    override suspend fun save(coupon: Coupon) = dao.upsert(CouponEntity.fromDomain(coupon))
}
