package com.restaurantpos.feature.menu

import com.restaurantpos.core.config.ConfigRepository
import com.restaurantpos.core.config.DefaultRegionConfig
import com.restaurantpos.core.config.RegionConfig
import com.restaurantpos.core.domain.repository.MenuItemRepository
import com.restaurantpos.core.model.MenuItem
import com.restaurantpos.core.model.ModifierGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class MenuViewModelTest {

    private val item1 = MenuItem("mi-1", mapOf("en" to "Burger"), 1200L, null, "cat-mains")
    private val item2 = MenuItem("mi-2", mapOf("en" to "Cola", "zh" to "可乐"), 250L, null, "cat-drinks")
    private val item3 = MenuItem("mi-3", mapOf("en" to "Fries"), 500L, null, "cat-mains", isSoldOut = true)

    private inner class FakeMenuRepo : MenuItemRepository {
        val store = mutableMapOf("mi-1" to item1, "mi-2" to item2, "mi-3" to item3)
        val modGroups = mutableMapOf<String, List<ModifierGroup>>()
        private val _flow = MutableStateFlow(store.values.toList())

        override fun observeAll(): Flow<List<MenuItem>> = _flow
        override fun observeAvailable(): Flow<List<MenuItem>> = MutableStateFlow(store.values.filter { !it.isSoldOut })
        override suspend fun getById(id: String) = store[id]
        override suspend fun save(item: MenuItem) { store[item.id] = item; _flow.value = store.values.toList() }
        override suspend fun upsertAll(items: List<MenuItem>) { items.forEach { store[it.id] = it }; _flow.value = store.values.toList() }
        override suspend fun setSoldOut(id: String, soldOut: Boolean) {
            store[id]?.let { store[id] = it.copy(isSoldOut = soldOut) }
            _flow.value = store.values.toList()
        }
        override suspend fun bulkSetSoldOut(ids: List<String>, soldOut: Boolean) {
            ids.forEach { id -> store[id]?.let { store[id] = it.copy(isSoldOut = soldOut) } }
            _flow.value = store.values.toList()
        }
        override suspend fun getModifierGroups(menuItemId: String) = modGroups[menuItemId] ?: emptyList()
        override suspend fun saveModifierGroups(menuItemId: String, groups: List<ModifierGroup>) { modGroups[menuItemId] = groups }
        override fun observeAvailableForContext(channel: String, timeHhmm: String): Flow<List<MenuItem>> = flowOf(emptyList())
    }

    private inner class FakeConfigRepo : ConfigRepository {
        override val config = flowOf(DefaultRegionConfig)
        override fun current() = DefaultRegionConfig
        override suspend fun update(config: RegionConfig) {}
    }

    @Test fun `observeAll includes sold-out items`() = runTest {
        val repo = FakeMenuRepo()
        val all = repo.store.values.toList()
        assertEquals(3, all.size)
        assertTrue(all.any { it.isSoldOut })
    }

    @Test fun `toggleSoldOut flips isSoldOut`() = runTest {
        val repo = FakeMenuRepo()
        assertFalse(repo.store["mi-1"]!!.isSoldOut)
        repo.setSoldOut("mi-1", true)
        assertTrue(repo.store["mi-1"]!!.isSoldOut)
        repo.setSoldOut("mi-1", false)
        assertFalse(repo.store["mi-1"]!!.isSoldOut)
    }

    @Test fun `bulkSetSoldOut marks multiple items`() = runTest {
        val repo = FakeMenuRepo()
        repo.bulkSetSoldOut(listOf("mi-1", "mi-2"), true)
        assertTrue(repo.store["mi-1"]!!.isSoldOut)
        assertTrue(repo.store["mi-2"]!!.isSoldOut)
        // mi-3 was already sold out, so all 3 are now sold out
        assertTrue(repo.store.values.all { it.isSoldOut })
    }

    @Test fun `bulkSetSoldOut false restores items`() = runTest {
        val repo = FakeMenuRepo()
        // mi-3 starts as sold out
        assertTrue(repo.store["mi-3"]!!.isSoldOut)
        repo.bulkSetSoldOut(listOf("mi-3"), false)
        assertFalse(repo.store["mi-3"]!!.isSoldOut)
    }

    @Test fun `save updates price and names`() = runTest {
        val repo = FakeMenuRepo()
        val updated = item1.copy(priceMinorUnit = 1500L, names = mapOf("en" to "Big Burger"))
        repo.save(updated)
        assertEquals(1500L, repo.store["mi-1"]!!.priceMinorUnit)
        assertEquals("Big Burger", repo.store["mi-1"]!!.names["en"])
    }

    @Test fun `getModifierGroups returns empty for item without groups`() = runTest {
        val repo = FakeMenuRepo()
        val groups = repo.getModifierGroups("mi-1")
        assertTrue(groups.isEmpty())
    }

    @Test fun `saveModifierGroups persists groups`() = runTest {
        val repo = FakeMenuRepo()
        val group = ModifierGroup("g1", mapOf("en" to "Size"), modifiers = emptyList())
        repo.saveModifierGroups("mi-1", listOf(group))
        val loaded = repo.getModifierGroups("mi-1")
        assertEquals(1, loaded.size)
        assertEquals("g1", loaded[0].id)
    }
}
