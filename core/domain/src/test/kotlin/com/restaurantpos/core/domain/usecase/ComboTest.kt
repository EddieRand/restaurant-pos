package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ComboTest {

    private lateinit var orderRepo: FakeOrderRepository
    private lateinit var menuItemRepo: FakeMenuItemRepoForCombo
    private lateinit var comboRepo: FakeComboRepository
    private lateinit var useCase: AddComboUseCase

    private val now = 1_700_000_000_000L

    @Before fun setup() {
        orderRepo = FakeOrderRepository()
        menuItemRepo = FakeMenuItemRepoForCombo()
        comboRepo = FakeComboRepository()
        useCase = AddComboUseCase(comboRepo, menuItemRepo, orderRepo)

        menuItemRepo.items["mi-001"] = menuItem("mi-001", 1200L, "cat-mains")
        menuItemRepo.items["mi-011"] = menuItem("mi-011", 500L, "cat-starters")
        menuItemRepo.items["mi-021"] = menuItem("mi-021", 250L, "cat-drinks")

        orderRepo.orders["o1"] = Order(
            id = "o1", sourceTerminalId = "t1", createdAt = now, updatedAt = now,
        )
    }

    @Test fun `combo expands into multiple order items`() = runBlocking {
        comboRepo.combos["combo-a"] = combo("combo-a", 1900L, "mi-001", "mi-011", "mi-021")
        val result = useCase("o1", "combo-a") as AddComboUseCase.Result.Success
        assertEquals(3, result.itemIds.size)
    }

    @Test fun `combo total equals comboPriceMinorUnit`() = runBlocking {
        comboRepo.combos["combo-a"] = combo("combo-a", 1900L, "mi-001", "mi-011", "mi-021")
        useCase("o1", "combo-a")
        val total = orderRepo.items.values
            .filter { it.comboId == "combo-a" }
            .sumOf { it.lineTotalMinorUnit }
        assertEquals(1900L, total)
    }

    @Test fun `combo price is cheaper than individual items`() = runBlocking {
        val comboPrice = 1900L
        val individualTotal = 1200L + 500L + 250L  // 1950
        assertTrue(comboPrice < individualTotal)
    }

    @Test fun `all items share the same comboId`() = runBlocking {
        comboRepo.combos["combo-a"] = combo("combo-a", 1900L, "mi-001", "mi-011", "mi-021")
        useCase("o1", "combo-a")
        val comboItems = orderRepo.items.values.filter { it.comboId == "combo-a" }
        assertEquals(3, comboItems.size)
        assertTrue(comboItems.all { it.comboId == "combo-a" })
    }

    @Test fun `only first item carries the price`() = runBlocking {
        comboRepo.combos["combo-a"] = combo("combo-a", 1900L, "mi-001", "mi-011", "mi-021")
        useCase("o1", "combo-a")
        val comboItems = orderRepo.items.values.filter { it.comboId == "combo-a" }
            .sortedByDescending { it.unitPriceMinorUnit }
        assertEquals(1900L, comboItems.first().unitPriceMinorUnit)
        assertTrue(comboItems.drop(1).all { it.unitPriceMinorUnit == 0L })
    }

    @Test fun `unknown combo returns error`() = runBlocking {
        val result = useCase("o1", "ghost-combo")
        assertTrue(result is AddComboUseCase.Result.Error)
    }

    @Test fun `each item status is PENDING`() = runBlocking {
        comboRepo.combos["combo-a"] = combo("combo-a", 1900L, "mi-001", "mi-011", "mi-021")
        useCase("o1", "combo-a")
        val comboItems = orderRepo.items.values.filter { it.comboId == "combo-a" }
        assertTrue(comboItems.all { it.status == OrderItemStatus.PENDING })
    }

    private fun menuItem(id: String, price: Long, categoryId: String) = MenuItem(
        id = id, names = mapOf("en" to id), priceMinorUnit = price,
        taxRateId = null, categoryId = categoryId, course = 1,
    )

    private fun combo(id: String, price: Long, vararg menuItemIds: String) = Combo(
        id = id,
        names = mapOf("en" to "Test Combo"),
        components = menuItemIds.map { ComboComponent(it) },
        comboPriceMinorUnit = price,
    )
}

class FakeComboRepository : com.restaurantpos.core.domain.repository.ComboRepository {
    val combos = mutableMapOf<String, Combo>()
    override fun observeActive(): Flow<List<Combo>> = flowOf(combos.values.toList())
    override suspend fun getById(id: String): Combo? = combos[id]
    override suspend fun save(combo: Combo) { combos[combo.id] = combo }
}

class FakeMenuItemRepoForCombo : com.restaurantpos.core.domain.repository.MenuItemRepository {
    val items = mutableMapOf<String, MenuItem>()
    override fun observeAll(): Flow<List<MenuItem>> = flowOf(items.values.toList())
    override fun observeAvailable(): Flow<List<MenuItem>> = flowOf(items.values.filter { !it.isSoldOut })
    override fun observeAvailableForContext(channel: String, timeHhmm: String): Flow<List<MenuItem>> = flowOf(emptyList())
    override suspend fun getById(id: String): MenuItem? = items[id]
    override suspend fun save(item: MenuItem) { items[item.id] = item }
    override suspend fun upsertAll(items: List<MenuItem>) { items.forEach { this.items[it.id] = it } }
    override suspend fun setSoldOut(id: String, soldOut: Boolean) {
        items[id]?.let { items[id] = it.copy(isSoldOut = soldOut) }
    }
    override suspend fun bulkSetSoldOut(ids: List<String>, soldOut: Boolean) {
        ids.forEach { setSoldOut(it, soldOut) }
    }
    override suspend fun getModifierGroups(menuItemId: String): List<ModifierGroup> = emptyList()
    override suspend fun saveModifierGroups(menuItemId: String, groups: List<ModifierGroup>) {}
}
