package com.restaurantpos.feature.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restaurantpos.core.config.ConfigRepository
import com.restaurantpos.core.config.RegionConfig
import com.restaurantpos.core.config.DefaultRegionConfig
import com.restaurantpos.core.domain.repository.MenuItemRepository
import com.restaurantpos.core.model.MenuItem
import com.restaurantpos.core.model.ModifierGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class MenuUiState(
    val items: List<MenuItem> = emptyList(),
    val regionConfig: RegionConfig = DefaultRegionConfig,
    val isLoading: Boolean = true,
    val editingItem: MenuItem? = null,
    val editingGroups: List<ModifierGroup> = emptyList(),
    val isEditDialogOpen: Boolean = false,
    val isSelectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val errorMessage: String? = null,
    val successMessage: String? = null,
) {
    val soldOutCount: Int get() = items.count { it.isSoldOut }
    val selectedCount: Int get() = selectedIds.size
}

@HiltViewModel
class MenuViewModel @Inject constructor(
    private val menuItemRepo: MenuItemRepository,
    private val configRepo: ConfigRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MenuUiState())
    val uiState: StateFlow<MenuUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            configRepo.config.collect { cfg ->
                _uiState.update { it.copy(regionConfig = cfg) }
            }
        }
        viewModelScope.launch {
            menuItemRepo.observeAll().collect { items ->
                _uiState.update { it.copy(items = items, isLoading = false) }
            }
        }
    }

    // --- Selection mode ---

    fun enterSelectionMode() {
        _uiState.update { it.copy(isSelectionMode = true, selectedIds = emptySet()) }
    }

    fun exitSelectionMode() {
        _uiState.update { it.copy(isSelectionMode = false, selectedIds = emptySet()) }
    }

    fun toggleItemSelection(id: String) {
        _uiState.update { s ->
            val updated = if (id in s.selectedIds) s.selectedIds - id else s.selectedIds + id
            s.copy(selectedIds = updated)
        }
    }

    fun selectAll() {
        _uiState.update { it.copy(selectedIds = it.items.map { i -> i.id }.toSet()) }
    }

    fun selectAllSoldOut() {
        _uiState.update { it.copy(selectedIds = it.items.filter { i -> i.isSoldOut }.map { i -> i.id }.toSet()) }
    }

    fun bulkMarkSoldOut(soldOut: Boolean) {
        val ids = _uiState.value.selectedIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                menuItemRepo.bulkSetSoldOut(ids, soldOut)
                val count = ids.size
                val msg = if (soldOut) "Marked $count item(s) as sold out" else "Cleared sold out for $count item(s)"
                _uiState.update { it.copy(isSelectionMode = false, selectedIds = emptySet(), successMessage = msg) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun clearAllSoldOut() {
        viewModelScope.launch {
            val soldOutIds = _uiState.value.items.filter { it.isSoldOut }.map { it.id }
            if (soldOutIds.isEmpty()) return@launch
            try {
                menuItemRepo.bulkSetSoldOut(soldOutIds, false)
                _uiState.update { it.copy(successMessage = "Cleared all sold-out items") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    // --- Edit ---

    fun startEdit(item: MenuItem) {
        viewModelScope.launch {
            val groups = menuItemRepo.getModifierGroups(item.id)
            _uiState.update { it.copy(editingItem = item, editingGroups = groups, isEditDialogOpen = true) }
        }
    }

    fun startNew() {
        val newItem = MenuItem(
            id = UUID.randomUUID().toString(),
            names = mapOf("en" to "", "zh" to ""),
            priceMinorUnit = 0L,
            taxRateId = null,
            categoryId = "cat-mains",
            course = 1,
        )
        _uiState.update { it.copy(editingItem = newItem, editingGroups = emptyList(), isEditDialogOpen = true) }
    }

    fun dismissEdit() {
        _uiState.update { it.copy(editingItem = null, editingGroups = emptyList(), isEditDialogOpen = false) }
    }

    fun saveItem(item: MenuItem, groups: List<ModifierGroup>) {
        viewModelScope.launch {
            try {
                menuItemRepo.save(item)
                if (groups.isNotEmpty()) {
                    menuItemRepo.saveModifierGroups(item.id, groups)
                }
                _uiState.update { it.copy(isEditDialogOpen = false, editingItem = null, editingGroups = emptyList(), successMessage = "Saved") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun toggleSoldOut(item: MenuItem) {
        viewModelScope.launch {
            menuItemRepo.setSoldOut(item.id, !item.isSoldOut)
        }
    }

    fun dismissMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
