package com.medpull.kiosk.ui.screens.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medpull.kiosk.R
import com.medpull.kiosk.data.models.InventoryItem
import com.medpull.kiosk.data.repository.InventoryRepository
import com.medpull.kiosk.utils.AppStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InventoryState(
    val items: List<InventoryItem> = emptyList(),
    val filteredItems: List<InventoryItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val selectedCategory: String? = null,
    val selectedLocation: String? = null,
    val showLowStockOnly: Boolean = false,
    val categories: List<String> = emptyList(),
    val locations: List<String> = emptyList()
)

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val appStrings: AppStrings
) : ViewModel() {

    private val _state = MutableStateFlow(InventoryState())
    val state: StateFlow<InventoryState> = _state.asStateFlow()

    init {
        loadInventory()
    }

    fun loadInventory() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            inventoryRepository.getInventory()
                .onSuccess { items ->
                    val categories = items.map { it.category }.distinct().sorted()
                    val locations = items.map { it.location }.distinct().sorted()
                    _state.update {
                        it.copy(
                            items = items,
                            categories = categories,
                            locations = locations,
                            isLoading = false
                        )
                    }
                    applyFilters()
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isLoading = false, error = e.message ?: appStrings.get(R.string.err_failed_load_inventory))
                    }
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true, error = null) }
            inventoryRepository.getInventory(forceRefresh = true)
                .onSuccess { items ->
                    val categories = items.map { it.category }.distinct().sorted()
                    val locations = items.map { it.location }.distinct().sorted()
                    _state.update {
                        it.copy(
                            items = items,
                            categories = categories,
                            locations = locations,
                            isRefreshing = false
                        )
                    }
                    applyFilters()
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isRefreshing = false, error = e.message ?: appStrings.get(R.string.err_failed_refresh))
                    }
                }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _state.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    fun onCategorySelected(category: String?) {
        _state.update { it.copy(selectedCategory = category) }
        applyFilters()
    }

    fun onLocationSelected(location: String?) {
        _state.update { it.copy(selectedLocation = location) }
        applyFilters()
    }

    fun onLowStockToggled(enabled: Boolean) {
        _state.update { it.copy(showLowStockOnly = enabled) }
        applyFilters()
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun applyFilters() {
        val current = _state.value
        var filtered = current.items

        if (current.searchQuery.isNotBlank()) {
            val query = current.searchQuery.lowercase()
            filtered = filtered.filter { item ->
                item.itemName.lowercase().contains(query) ||
                        item.itemType.lowercase().contains(query) ||
                        item.boxLabel.lowercase().contains(query) ||
                        item.category.lowercase().contains(query) ||
                        item.location.lowercase().contains(query) ||
                        item.room.lowercase().contains(query)
            }
        }

        current.selectedCategory?.let { cat ->
            filtered = filtered.filter { it.category == cat }
        }

        current.selectedLocation?.let { loc ->
            filtered = filtered.filter { it.location == loc }
        }

        if (current.showLowStockOnly) {
            filtered = filtered.filter { it.isLowStock }
        }

        _state.update { it.copy(filteredItems = filtered) }
    }
}
