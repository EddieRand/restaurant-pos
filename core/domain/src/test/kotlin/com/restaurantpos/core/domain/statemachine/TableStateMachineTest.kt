package com.restaurantpos.core.domain.statemachine

import com.restaurantpos.core.model.TableStatus
import org.junit.Assert.*
import org.junit.Test

class TableStateMachineTest {

    // ── Legal transitions ────────────────────────────────────────────────────

    @Test fun `AVAILABLE + seated = OCCUPIED`() =
        assertEquals(TableStatus.OCCUPIED, TableStateMachine.onSeated(TableStatus.AVAILABLE))

    @Test fun `OCCUPIED + firstOrderFired = ORDERED`() =
        assertEquals(TableStatus.ORDERED, TableStateMachine.onFirstOrderFired(TableStatus.OCCUPIED))

    @Test fun `ORDERED + checkoutStarted = CHECKOUT`() =
        assertEquals(TableStatus.CHECKOUT, TableStateMachine.onCheckoutStarted(TableStatus.ORDERED))

    @Test fun `CHECKOUT + paymentComplete = DIRTY`() =
        assertEquals(TableStatus.DIRTY, TableStateMachine.onPaymentComplete(TableStatus.CHECKOUT))

    @Test fun `DIRTY + cleared = AVAILABLE`() =
        assertEquals(TableStatus.AVAILABLE, TableStateMachine.onCleared(TableStatus.DIRTY))

    @Test fun `AVAILABLE + reserved = RESERVED`() =
        assertEquals(TableStatus.RESERVED, TableStateMachine.onReserved(TableStatus.AVAILABLE))

    @Test fun `RESERVED + released = AVAILABLE`() =
        assertEquals(TableStatus.AVAILABLE, TableStateMachine.onReservationReleased(TableStatus.RESERVED))

    // ── Illegal transitions ──────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `OCCUPIED + seated throws`() { TableStateMachine.onSeated(TableStatus.OCCUPIED) }

    @Test(expected = IllegalArgumentException::class)
    fun `AVAILABLE + firstOrderFired throws`() { TableStateMachine.onFirstOrderFired(TableStatus.AVAILABLE) }

    @Test(expected = IllegalArgumentException::class)
    fun `AVAILABLE + checkoutStarted throws`() { TableStateMachine.onCheckoutStarted(TableStatus.AVAILABLE) }

    @Test(expected = IllegalArgumentException::class)
    fun `OCCUPIED + paymentComplete throws`() { TableStateMachine.onPaymentComplete(TableStatus.OCCUPIED) }

    @Test(expected = IllegalArgumentException::class)
    fun `AVAILABLE + cleared throws`() { TableStateMachine.onCleared(TableStatus.AVAILABLE) }

    @Test(expected = IllegalArgumentException::class)
    fun `ORDERED + reserved throws`() { TableStateMachine.onReserved(TableStatus.ORDERED) }

    @Test(expected = IllegalArgumentException::class)
    fun `AVAILABLE + reservationReleased throws`() { TableStateMachine.onReservationReleased(TableStatus.AVAILABLE) }
}
