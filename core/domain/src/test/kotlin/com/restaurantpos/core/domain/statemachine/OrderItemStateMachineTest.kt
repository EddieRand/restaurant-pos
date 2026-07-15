package com.restaurantpos.core.domain.statemachine

import com.restaurantpos.core.model.OrderItemStatus
import org.junit.Assert.*
import org.junit.Test

class OrderItemStateMachineTest {

    // ── Legal transitions ─────────────────────────────────────────────────

    @Test fun `PENDING + fire = PLACED`() =
        assertEquals(OrderItemStatus.PLACED, OrderItemStateMachine.onFire(OrderItemStatus.PENDING))

    @Test fun `PLACED + kitchenAcknowledge = PREPARING`() =
        assertEquals(OrderItemStatus.PREPARING, OrderItemStateMachine.onKitchenAcknowledge(OrderItemStatus.PLACED))

    @Test fun `PREPARING + serve = SERVED`() =
        assertEquals(OrderItemStatus.SERVED, OrderItemStateMachine.onServe(OrderItemStatus.PREPARING))

    // Refund branch — from PLACED
    @Test fun `PLACED + refund = REFUNDED`() =
        assertEquals(OrderItemStatus.REFUNDED, OrderItemStateMachine.onRefund(OrderItemStatus.PLACED))

    // Refund branch — from PREPARING
    @Test fun `PREPARING + refund = REFUNDED`() =
        assertEquals(OrderItemStatus.REFUNDED, OrderItemStateMachine.onRefund(OrderItemStatus.PREPARING))

    // Refund branch — from SERVED
    @Test fun `SERVED + refund = REFUNDED`() =
        assertEquals(OrderItemStatus.REFUNDED, OrderItemStateMachine.onRefund(OrderItemStatus.SERVED))

    // Comp branch — from PENDING
    @Test fun `PENDING + comp = COMPED`() =
        assertEquals(OrderItemStatus.COMPED, OrderItemStateMachine.onComp(OrderItemStatus.PENDING))

    // Comp branch — from PLACED
    @Test fun `PLACED + comp = COMPED`() =
        assertEquals(OrderItemStatus.COMPED, OrderItemStateMachine.onComp(OrderItemStatus.PLACED))

    // Comp branch — from PREPARING
    @Test fun `PREPARING + comp = COMPED`() =
        assertEquals(OrderItemStatus.COMPED, OrderItemStateMachine.onComp(OrderItemStatus.PREPARING))

    // Comp branch — from SERVED
    @Test fun `SERVED + comp = COMPED`() =
        assertEquals(OrderItemStatus.COMPED, OrderItemStateMachine.onComp(OrderItemStatus.SERVED))

    // ── Illegal transitions — must use block body so return type is Unit ──

    // onFire: only PENDING allowed
    @Test(expected = IllegalArgumentException::class)
    fun `PLACED + fire throws`() { OrderItemStateMachine.onFire(OrderItemStatus.PLACED) }

    @Test(expected = IllegalArgumentException::class)
    fun `PREPARING + fire throws`() { OrderItemStateMachine.onFire(OrderItemStatus.PREPARING) }

    @Test(expected = IllegalArgumentException::class)
    fun `SERVED + fire throws`() { OrderItemStateMachine.onFire(OrderItemStatus.SERVED) }

    @Test(expected = IllegalArgumentException::class)
    fun `REFUNDED + fire throws`() { OrderItemStateMachine.onFire(OrderItemStatus.REFUNDED) }

    @Test(expected = IllegalArgumentException::class)
    fun `COMPED + fire throws`() { OrderItemStateMachine.onFire(OrderItemStatus.COMPED) }

    // onKitchenAcknowledge: only PLACED allowed
    @Test(expected = IllegalArgumentException::class)
    fun `PENDING + kitchenAcknowledge throws`() { OrderItemStateMachine.onKitchenAcknowledge(OrderItemStatus.PENDING) }

    @Test(expected = IllegalArgumentException::class)
    fun `PREPARING + kitchenAcknowledge throws`() { OrderItemStateMachine.onKitchenAcknowledge(OrderItemStatus.PREPARING) }

    @Test(expected = IllegalArgumentException::class)
    fun `SERVED + kitchenAcknowledge throws`() { OrderItemStateMachine.onKitchenAcknowledge(OrderItemStatus.SERVED) }

    // onServe: only PREPARING allowed
    @Test(expected = IllegalArgumentException::class)
    fun `PENDING + serve throws`() { OrderItemStateMachine.onServe(OrderItemStatus.PENDING) }

    @Test(expected = IllegalArgumentException::class)
    fun `PLACED + serve throws`() { OrderItemStateMachine.onServe(OrderItemStatus.PLACED) }

    @Test(expected = IllegalArgumentException::class)
    fun `SERVED + serve throws`() { OrderItemStateMachine.onServe(OrderItemStatus.SERVED) }

    // onRefund: only PLACED / PREPARING / SERVED allowed
    @Test(expected = IllegalArgumentException::class)
    fun `PENDING + refund throws`() { OrderItemStateMachine.onRefund(OrderItemStatus.PENDING) }

    @Test(expected = IllegalArgumentException::class)
    fun `REFUNDED + refund throws`() { OrderItemStateMachine.onRefund(OrderItemStatus.REFUNDED) }

    @Test(expected = IllegalArgumentException::class)
    fun `COMPED + refund throws`() { OrderItemStateMachine.onRefund(OrderItemStatus.COMPED) }

    // onComp: disallowed from final states REFUNDED and COMPED
    @Test(expected = IllegalArgumentException::class)
    fun `REFUNDED + comp throws`() { OrderItemStateMachine.onComp(OrderItemStatus.REFUNDED) }

    @Test(expected = IllegalArgumentException::class)
    fun `COMPED + comp throws`() { OrderItemStateMachine.onComp(OrderItemStatus.COMPED) }
}
