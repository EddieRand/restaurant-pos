package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CouponTest {

    private lateinit var orderRepo: FakeOrderRepository
    private lateinit var couponRepo: FakeCouponRepository
    private lateinit var useCase: ApplyCouponUseCase

    private val now = 1_700_000_000_000L

    private fun order(id: String, subtotal: Long) = Order(
        id = id,
        sourceTerminalId = "t1",
        subtotalMinorUnit = subtotal,
        createdAt = now,
        updatedAt = now,
    )

    @Before fun setup() {
        orderRepo = FakeOrderRepository()
        couponRepo = FakeCouponRepository()
        useCase = ApplyCouponUseCase(couponRepo, orderRepo)
    }

    @Test fun `percent coupon calculates correct discount`() = runBlocking {
        orderRepo.orders["o1"] = order("o1", 10000L)
        couponRepo.coupons["SAVE10"] = Coupon("c1", "SAVE10", CouponType.PERCENT, 10L, now + 1000L)
        val result = useCase("o1", "SAVE10", now) as ApplyCouponUseCase.Result.Success
        assertEquals(1000L, result.discountMinorUnit)
    }

    @Test fun `fixed coupon deducts exact amount`() = runBlocking {
        orderRepo.orders["o2"] = order("o2", 5000L)
        couponRepo.coupons["FLAT200"] = Coupon("c2", "FLAT200", CouponType.FIXED, 200L, now + 1000L)
        val result = useCase("o2", "FLAT200", now) as ApplyCouponUseCase.Result.Success
        assertEquals(200L, result.discountMinorUnit)
    }

    @Test fun `fixed coupon capped at subtotal`() = runBlocking {
        orderRepo.orders["o3"] = order("o3", 100L)
        couponRepo.coupons["BIG"] = Coupon("c3", "BIG", CouponType.FIXED, 9999L, now + 1000L)
        val result = useCase("o3", "BIG", now) as ApplyCouponUseCase.Result.Success
        assertEquals(100L, result.discountMinorUnit)
    }

    @Test fun `expired coupon returns error`() = runBlocking {
        orderRepo.orders["o4"] = order("o4", 5000L)
        couponRepo.coupons["OLD"] = Coupon("c4", "OLD", CouponType.PERCENT, 20L, now - 1L)
        val result = useCase("o4", "OLD", now)
        assertTrue(result is ApplyCouponUseCase.Result.Error)
    }

    @Test fun `unknown code returns error`() = runBlocking {
        orderRepo.orders["o5"] = order("o5", 5000L)
        val result = useCase("o5", "GHOST", now)
        assertTrue(result is ApplyCouponUseCase.Result.Error)
    }

    @Test fun `inactive coupon returns error`() = runBlocking {
        orderRepo.orders["o6"] = order("o6", 5000L)
        couponRepo.coupons["OFF"] = Coupon("c5", "OFF", CouponType.PERCENT, 10L, now + 1000L, isActive = false)
        val result = useCase("o6", "OFF", now)
        assertTrue(result is ApplyCouponUseCase.Result.Error)
    }

    @Test fun `100 percent coupon discount equals subtotal`() = runBlocking {
        orderRepo.orders["o7"] = order("o7", 8000L)
        couponRepo.coupons["FREE"] = Coupon("c6", "FREE", CouponType.PERCENT, 100L, now + 1000L)
        val result = useCase("o7", "FREE", now) as ApplyCouponUseCase.Result.Success
        assertEquals(8000L, result.discountMinorUnit)
    }

    @Test fun `coupon discount is persisted on order`() = runBlocking {
        orderRepo.orders["o8"] = order("o8", 6000L)
        couponRepo.coupons["HALF"] = Coupon("c7", "HALF", CouponType.PERCENT, 50L, now + 1000L)
        useCase("o8", "HALF", now)
        assertEquals(3000L, orderRepo.orders["o8"]!!.discountMinorUnit)
    }
}

class FakeCouponRepository : com.restaurantpos.core.domain.repository.CouponRepository {
    val coupons = mutableMapOf<String, Coupon>()
    override suspend fun getByCode(code: String): Coupon? = coupons[code]
    override suspend fun save(coupon: Coupon) { coupons[coupon.code] = coupon }
}
