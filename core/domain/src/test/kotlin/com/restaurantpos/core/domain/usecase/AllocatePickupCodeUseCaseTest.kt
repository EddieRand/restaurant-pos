package com.restaurantpos.core.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AllocatePickupCodeUseCaseTest {

    private val now = 1_700_000_000_000L // fixed reference instant

    @Test fun `first order of the day gets pickup code 1`() = runTest {
        val orderRepo = FakeOrderRepository()
        val useCase = AllocatePickupCodeUseCase(orderRepo)

        val code = useCase(now)

        assertEquals("1", code)
    }

    @Test fun `increments from the count of codes allocated today`() = runTest {
        val orderRepo = FakeOrderRepository()
        orderRepo.pickupCodesAllocated = 41
        val useCase = AllocatePickupCodeUseCase(orderRepo)

        val code = useCase(now)

        assertEquals("42", code)
    }

    @Test fun `wraps back to 1 after reaching 99`() = runTest {
        val orderRepo = FakeOrderRepository()
        orderRepo.pickupCodesAllocated = 99
        val useCase = AllocatePickupCodeUseCase(orderRepo)

        val code = useCase(now)

        assertEquals("1", code)
    }

    @Test fun `keeps advancing after the first wrap instead of repeating 1`() = runTest {
        // Regression for F-006: MAX-based logic returned "1" for every order
        // after the day's first wrap (99 stays the max all day).
        val orderRepo = FakeOrderRepository()
        orderRepo.pickupCodesAllocated = 100 // 99 codes + one wrapped "1" already given out
        val useCase = AllocatePickupCodeUseCase(orderRepo)

        val code = useCase(now)

        assertEquals("2", code)
    }

    @Test fun `second wrap also continues the cycle`() = runTest {
        val orderRepo = FakeOrderRepository()
        orderRepo.pickupCodesAllocated = 99 * 2
        val useCase = AllocatePickupCodeUseCase(orderRepo)

        val code = useCase(now)

        assertEquals("1", code)
    }
}
