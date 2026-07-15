package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.domain.repository.MenuItemRepository
import com.restaurantpos.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ModifierGroupTest {

    private val cheeseburgerGroups = listOf(
        ModifierGroup(
            id = "mg-cook",
            names = mapOf("en" to "Cook Level", "zh" to "熟度"),
            type = ModifierGroupType.SINGLE,
            required = true,
            minSelect = 1,
            maxSelect = 1,
            modifiers = listOf(
                Modifier("m-rare", mapOf("en" to "Rare"), 0L),
                Modifier("m-well", mapOf("en" to "Well Done"), 0L),
            ),
        ),
        ModifierGroup(
            id = "mg-extra",
            names = mapOf("en" to "Extras"),
            type = ModifierGroupType.MULTI,
            required = false,
            minSelect = 0,
            maxSelect = 3,
            modifiers = listOf(
                Modifier("m-cheese", mapOf("en" to "Extra Cheese"), 150L),
                Modifier("m-bacon",  mapOf("en" to "Bacon"),        200L),
            ),
        ),
    )

    private inner class FakeMenuItemRepo : MenuItemRepository {
        val modifierStore = mutableMapOf<String, List<ModifierGroup>>()
        override fun observeAll(): Flow<List<MenuItem>> = flowOf(emptyList())
        override fun observeAvailable(): Flow<List<MenuItem>> = flowOf(emptyList())
        override fun observeAvailableForContext(channel: String, timeHhmm: String): Flow<List<MenuItem>> = flowOf(emptyList())
        override suspend fun getById(id: String): MenuItem? = null
        override suspend fun save(item: MenuItem) {}
        override suspend fun upsertAll(items: List<MenuItem>) {}
        override suspend fun setSoldOut(id: String, soldOut: Boolean) {}
        override suspend fun bulkSetSoldOut(ids: List<String>, soldOut: Boolean) {}
        override suspend fun getModifierGroups(menuItemId: String) = modifierStore[menuItemId] ?: emptyList()
        override suspend fun saveModifierGroups(menuItemId: String, groups: List<ModifierGroup>) {
            modifierStore[menuItemId] = groups
        }
    }

    @Test fun `saveModifierGroups and retrieve round-trips correctly`() = runTest {
        val repo = FakeMenuItemRepo()
        repo.saveModifierGroups("mi-001", cheeseburgerGroups)
        val loaded = repo.getModifierGroups("mi-001")
        assertEquals(2, loaded.size)
        assertEquals("mg-cook", loaded[0].id)
        assertEquals(2, loaded[0].modifiers.size)
        assertEquals("mg-extra", loaded[1].id)
    }

    @Test fun `getModifierGroups returns empty for item with no groups`() = runTest {
        val repo = FakeMenuItemRepo()
        val groups = repo.getModifierGroups("mi-999")
        assertTrue(groups.isEmpty())
    }

    @Test fun `modifier price adjustment preserved`() = runTest {
        val repo = FakeMenuItemRepo()
        repo.saveModifierGroups("mi-001", cheeseburgerGroups)
        val loaded = repo.getModifierGroups("mi-001")
        val extras = loaded.find { it.id == "mg-extra" }!!
        assertEquals(150L, extras.modifiers.find { it.id == "m-cheese" }!!.priceAdjustmentMinorUnit)
        assertEquals(200L, extras.modifiers.find { it.id == "m-bacon" }!!.priceAdjustmentMinorUnit)
    }

    @Test fun `required group validation - single`() = runTest {
        val repo = FakeMenuItemRepo()
        repo.saveModifierGroups("mi-001", cheeseburgerGroups)
        val groups = repo.getModifierGroups("mi-001")
        val required = groups.filter { it.required }
        assertEquals(1, required.size)
        assertEquals("mg-cook", required[0].id)
        assertTrue(required[0].minSelect >= 1)
    }

    @Test fun `overwrite modifier groups replaces previous`() = runTest {
        val repo = FakeMenuItemRepo()
        repo.saveModifierGroups("mi-001", cheeseburgerGroups)
        val newGroups = listOf(
            ModifierGroup("mg-new", mapOf("en" to "New"), ModifierGroupType.SINGLE, false, 0, 1,
                listOf(Modifier("m-a", mapOf("en" to "A"), 0L)))
        )
        repo.saveModifierGroups("mi-001", newGroups)
        val loaded = repo.getModifierGroups("mi-001")
        assertEquals(1, loaded.size)
        assertEquals("mg-new", loaded[0].id)
    }
}
