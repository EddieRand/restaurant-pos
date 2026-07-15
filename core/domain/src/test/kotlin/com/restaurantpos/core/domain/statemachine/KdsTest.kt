package com.restaurantpos.core.domain.statemachine

import com.restaurantpos.core.domain.repository.KitchenTicketRepository
import com.restaurantpos.core.domain.routing.KitchenRouter
import com.restaurantpos.core.domain.usecase.BumpTicketUseCase
import com.restaurantpos.core.domain.usecase.FakeOrderRepository
import com.restaurantpos.core.domain.usecase.FireKitchenTicketsUseCase
import com.restaurantpos.core.domain.usecase.RecallTicketUseCase
import com.restaurantpos.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class KdsTest {

    // ── State machine ─────────────────────────────────────────────────────────

    @Test fun `NEW to PREPARING via onStartPreparing`() {
        val s = KitchenTicketStateMachine.onStartPreparing(KitchenTicketStatus.NEW)
        assertEquals(KitchenTicketStatus.PREPARING, s)
    }

    @Test fun `PREPARING to DONE via onBump`() {
        val s = KitchenTicketStateMachine.onBump(KitchenTicketStatus.PREPARING)
        assertEquals(KitchenTicketStatus.DONE, s)
    }

    @Test fun `NEW to DONE via onBump (shortcut)`() {
        val s = KitchenTicketStateMachine.onBump(KitchenTicketStatus.NEW)
        assertEquals(KitchenTicketStatus.DONE, s)
    }

    @Test fun `DONE to RECALLED via onRecall`() {
        val s = KitchenTicketStateMachine.onRecall(KitchenTicketStatus.DONE)
        assertEquals(KitchenTicketStatus.RECALLED, s)
    }

    @Test fun `RECALLED to PREPARING via onRestartFromRecall`() {
        val s = KitchenTicketStateMachine.onRestartFromRecall(KitchenTicketStatus.RECALLED)
        assertEquals(KitchenTicketStatus.PREPARING, s)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `bump from DONE throws`() {
        KitchenTicketStateMachine.onBump(KitchenTicketStatus.DONE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `recall from PREPARING throws`() {
        KitchenTicketStateMachine.onRecall(KitchenTicketStatus.PREPARING)
    }

    // ── FakeKitchenTicketRepository ───────────────────────────────────────────

    private class FakeTicketRepo : KitchenTicketRepository {
        val store = mutableMapOf<String, KitchenTicket>()
        override fun observeActive(): Flow<List<KitchenTicket>> = flowOf(store.values.filter { it.status != KitchenTicketStatus.DONE }.toList())
        override fun observeByStation(stationId: String): Flow<List<KitchenTicket>> = flowOf(
            store.values.filter { it.stationId == stationId && it.status != KitchenTicketStatus.DONE }.toList()
        )
        override suspend fun getById(id: String) = store[id]
        override suspend fun getByOrder(orderId: String) = store.values.filter { it.orderId == orderId }
        override suspend fun save(ticket: KitchenTicket) { store[ticket.id] = ticket }
        override suspend fun saveAll(tickets: List<KitchenTicket>) { tickets.forEach { store[it.id] = it } }
        override suspend fun updateStatus(id: String, status: KitchenTicketStatus, bumpedAt: Long?) {
            store[id]?.let { store[id] = it.copy(status = status, bumpedAt = bumpedAt) }
        }
        override suspend fun applyRemote(tickets: List<KitchenTicket>) { tickets.forEach { store[it.id] = it } }
    }

    // Use FakeOrderRepository directly — it has all needed overrides

    private fun makeTicket(stationId: String = "kitchen", orderId: String = "o1") = KitchenTicket(
        id = UUID.randomUUID().toString(),
        orderId = orderId,
        orderItemIds = listOf("i1"),
        stationId = stationId,
        createdAt = System.currentTimeMillis(),
    )

    // ── BumpTicketUseCase ──────────────────────────────────────────────────────

    @Test fun `bump NEW ticket → DONE`() = runTest {
        val repo = FakeTicketRepo()
        val ticket = makeTicket()
        repo.store[ticket.id] = ticket
        BumpTicketUseCase(repo, FakeOrderRepository())(ticket.id)
        assertEquals(KitchenTicketStatus.DONE, repo.store[ticket.id]!!.status)
    }

    @Test fun `bump sets bumpedAt timestamp`() = runTest {
        val repo = FakeTicketRepo()
        val ticket = makeTicket()
        repo.store[ticket.id] = ticket
        val before = System.currentTimeMillis()
        BumpTicketUseCase(repo, FakeOrderRepository())(ticket.id)
        val bumpedAt = repo.store[ticket.id]!!.bumpedAt!!
        assertTrue(bumpedAt >= before)
    }

    @Test fun `bumping last ticket to DONE marks order ready for pickup`() = runTest {
        val repo = FakeTicketRepo()
        val orderRepo = FakeOrderRepository()
        val orderId = "o-pickup"
        orderRepo.orders[orderId] = Order(
            id = orderId, sourceTerminalId = "term-1", createdAt = 0L, updatedAt = 0L,
        )
        val ticket1 = makeTicket(orderId = orderId).copy(status = KitchenTicketStatus.DONE)
        val ticket2 = makeTicket(orderId = orderId)
        repo.store[ticket1.id] = ticket1
        repo.store[ticket2.id] = ticket2

        BumpTicketUseCase(repo, orderRepo)(ticket2.id)

        assertEquals(OrderFulfillmentStatus.READY_FOR_PICKUP, orderRepo.orders[orderId]!!.fulfillmentStatus)
    }

    @Test fun `bumping ticket while others still pending does not mark order ready`() = runTest {
        val repo = FakeTicketRepo()
        val orderRepo = FakeOrderRepository()
        val orderId = "o-pickup-2"
        orderRepo.orders[orderId] = Order(
            id = orderId, sourceTerminalId = "term-1", createdAt = 0L, updatedAt = 0L,
        )
        val ticket1 = makeTicket(orderId = orderId)
        val ticket2 = makeTicket(orderId = orderId)
        repo.store[ticket1.id] = ticket1
        repo.store[ticket2.id] = ticket2

        BumpTicketUseCase(repo, orderRepo)(ticket1.id)

        assertEquals(OrderFulfillmentStatus.NOT_READY, orderRepo.orders[orderId]!!.fulfillmentStatus)
    }

    // ── RecallTicketUseCase ────────────────────────────────────────────────────

    @Test fun `recall DONE ticket → PREPARING`() = runTest {
        val repo = FakeTicketRepo()
        val ticket = makeTicket().copy(status = KitchenTicketStatus.DONE)
        repo.store[ticket.id] = ticket
        RecallTicketUseCase(repo)(ticket.id)
        assertEquals(KitchenTicketStatus.PREPARING, repo.store[ticket.id]!!.status)
    }

    // ── FireKitchenTicketsUseCase ──────────────────────────────────────────────

    @Test fun `fire tickets groups items by station`() = runTest {
        val orderRepo = FakeOrderRepository()
        val ticketRepo = FakeTicketRepo()
        val router = KitchenRouter(
            routes = mapOf("cat-mains" to "hot-kitchen", "cat-drinks" to "bar"),
        )

        val orderId = "o-fire"
        val item1 = makeOrderItem(orderId, categoryId = "cat-mains")
        val item2 = makeOrderItem(orderId, categoryId = "cat-drinks")
        val item3 = makeOrderItem(orderId, categoryId = "cat-mains")
        orderRepo.items[item1.id] = item1
        orderRepo.items[item2.id] = item2
        orderRepo.items[item3.id] = item3

        val tickets = FireKitchenTicketsUseCase(orderRepo, ticketRepo, router)(orderId)

        assertEquals(2, tickets.size)
        val hotTicket = tickets.find { it.stationId == "hot-kitchen" }!!
        assertEquals(2, hotTicket.orderItemIds.size)
        val barTicket = tickets.find { it.stationId == "bar" }!!
        assertEquals(1, barTicket.orderItemIds.size)
    }

    @Test fun `fire tickets with no placed items returns empty`() = runTest {
        val orderRepo = FakeOrderRepository()
        val ticketRepo = FakeTicketRepo()
        val router = KitchenRouter(emptyMap())

        val orderId = "o-empty"
        // Items exist but are PENDING (not PLACED), so they won't be fired
        val item = makeOrderItem(orderId, status = OrderItemStatus.PENDING)
        orderRepo.items[item.id] = item

        val tickets = FireKitchenTicketsUseCase(orderRepo, ticketRepo, router)(orderId)
        assertEquals(0, tickets.size)
    }

    @Test fun `re-fire only tickets items not already on a ticket`() = runTest {
        val orderRepo = FakeOrderRepository()
        val ticketRepo = FakeTicketRepo()
        val router = KitchenRouter(emptyMap(), defaultStationId = "main-kitchen")
        val fire = FireKitchenTicketsUseCase(orderRepo, ticketRepo, router)

        val orderId = "o-refire"
        val item1 = makeOrderItem(orderId)
        orderRepo.items[item1.id] = item1
        assertEquals(1, fire(orderId).size)

        // item1 is still PLACED but already ticketed — only the new item gets a ticket
        val item2 = makeOrderItem(orderId)
        orderRepo.items[item2.id] = item2
        val second = fire(orderId)
        assertEquals(1, second.size)
        assertEquals(listOf(item2.id), second.first().orderItemIds)

        // Nothing new → no tickets
        assertEquals(0, fire(orderId).size)
    }

    @Test fun `fire tickets all go to default station when no routes configured`() = runTest {
        val orderRepo = FakeOrderRepository()
        val ticketRepo = FakeTicketRepo()
        val router = KitchenRouter(emptyMap(), defaultStationId = "main-kitchen")

        val orderId = "o-default"
        val item1 = makeOrderItem(orderId, categoryId = "cat-mains")
        val item2 = makeOrderItem(orderId, categoryId = "cat-drinks")
        orderRepo.items[item1.id] = item1
        orderRepo.items[item2.id] = item2

        val tickets = FireKitchenTicketsUseCase(orderRepo, ticketRepo, router)(orderId)

        assertEquals(1, tickets.size)
        assertEquals("main-kitchen", tickets.first().stationId)
    }

    private fun makeOrderItem(
        orderId: String,
        categoryId: String? = "cat-mains",
        status: OrderItemStatus = OrderItemStatus.PLACED,
    ) = OrderItem(
        id = UUID.randomUUID().toString(),
        orderId = orderId,
        menuItemId = "mi-1",
        menuItemNameSnapshot = mapOf("en" to "Burger"),
        quantity = 1,
        unitPriceMinorUnit = 1000L,
        taxRateId = null,
        categoryId = categoryId,
        status = status,
    )
}
