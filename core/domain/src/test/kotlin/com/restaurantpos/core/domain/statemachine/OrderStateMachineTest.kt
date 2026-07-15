package com.restaurantpos.core.domain.statemachine

import com.restaurantpos.core.model.OrderStatus
import org.junit.Assert.*
import org.junit.Test

class OrderStateMachineTest {

    // ── Legal transitions ────────────────────────────────────────────────────

    @Test fun `DRAFT + firstItemAdded = IN_PROGRESS`() =
        assertEquals(OrderStatus.IN_PROGRESS, OrderStateMachine.onFirstItemAdded(OrderStatus.DRAFT))

    @Test fun `IN_PROGRESS + fire = PLACED`() =
        assertEquals(OrderStatus.PLACED, OrderStateMachine.onFire(OrderStatus.IN_PROGRESS))

    @Test fun `PLACED + allServed = READY`() =
        assertEquals(OrderStatus.READY, OrderStateMachine.onAllServed(OrderStatus.PLACED))

    @Test fun `READY + paymentComplete = CLOSED`() =
        assertEquals(OrderStatus.CLOSED, OrderStateMachine.onPaymentComplete(OrderStatus.READY))

    @Test fun `PLACED + additionalItemsFired = PLACED`() =
        assertEquals(OrderStatus.PLACED, OrderStateMachine.onAdditionalItemsFired(OrderStatus.PLACED))

    @Test fun `READY + additionalItemsFired = PLACED`() =
        assertEquals(OrderStatus.PLACED, OrderStateMachine.onAdditionalItemsFired(OrderStatus.READY))

    @Test fun `DRAFT + void = VOIDED`() =
        assertEquals(OrderStatus.VOIDED, OrderStateMachine.onVoid(OrderStatus.DRAFT))

    @Test fun `IN_PROGRESS + void = VOIDED`() =
        assertEquals(OrderStatus.VOIDED, OrderStateMachine.onVoid(OrderStatus.IN_PROGRESS))

    @Test fun `PLACED + void = VOIDED`() =
        assertEquals(OrderStatus.VOIDED, OrderStateMachine.onVoid(OrderStatus.PLACED))

    @Test fun `READY + void = VOIDED`() =
        assertEquals(OrderStatus.VOIDED, OrderStateMachine.onVoid(OrderStatus.READY))

    // ── Illegal transitions — must use block body so return type is Unit ─────

    @Test(expected = IllegalArgumentException::class)
    fun `IN_PROGRESS + firstItemAdded throws`() { OrderStateMachine.onFirstItemAdded(OrderStatus.IN_PROGRESS) }

    @Test(expected = IllegalArgumentException::class)
    fun `DRAFT + fire throws`() { OrderStateMachine.onFire(OrderStatus.DRAFT) }

    @Test(expected = IllegalArgumentException::class)
    fun `READY + fire throws`() { OrderStateMachine.onFire(OrderStatus.READY) }

    @Test(expected = IllegalArgumentException::class)
    fun `IN_PROGRESS + allServed throws`() { OrderStateMachine.onAllServed(OrderStatus.IN_PROGRESS) }

    @Test(expected = IllegalArgumentException::class)
    fun `DRAFT + paymentComplete throws`() { OrderStateMachine.onPaymentComplete(OrderStatus.DRAFT) }

    @Test(expected = IllegalArgumentException::class)
    fun `PLACED + paymentComplete throws`() { OrderStateMachine.onPaymentComplete(OrderStatus.PLACED) }

    @Test(expected = IllegalArgumentException::class)
    fun `DRAFT + additionalItemsFired throws`() { OrderStateMachine.onAdditionalItemsFired(OrderStatus.DRAFT) }

    @Test(expected = IllegalArgumentException::class)
    fun `CLOSED + additionalItemsFired throws`() { OrderStateMachine.onAdditionalItemsFired(OrderStatus.CLOSED) }

    @Test(expected = IllegalArgumentException::class)
    fun `CLOSED + void throws`() { OrderStateMachine.onVoid(OrderStatus.CLOSED) }

    @Test(expected = IllegalArgumentException::class)
    fun `VOIDED + void throws`() { OrderStateMachine.onVoid(OrderStatus.VOIDED) }
}
