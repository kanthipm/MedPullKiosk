package com.medpull.kiosk.ui.screens.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.medpull.kiosk.R
import com.medpull.kiosk.data.models.InventoryItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: InventoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.inventory_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.inventory_refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.inventory_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.inventory_clear_search))
                        }
                    }
                },
                singleLine = true
            )

            // Filter chips row
            FilterChipsRow(
                state = state,
                onCategorySelected = { viewModel.onCategorySelected(it) },
                onLocationSelected = { viewModel.onLocationSelected(it) },
                onLowStockToggled = { viewModel.onLowStockToggled(it) }
            )

            // Content
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.filteredItems.isEmpty() && !state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Inventory2,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.inventory_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
                else -> {
                    if (state.isRefreshing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.filteredItems, key = { "${it.room}-${it.location}-${it.itemName}" }) { item ->
                            InventoryItemCard(item = item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipsRow(
    state: InventoryState,
    onCategorySelected: (String?) -> Unit,
    onLocationSelected: (String?) -> Unit,
    onLowStockToggled: (Boolean) -> Unit
) {
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showLocationMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Low stock toggle
        FilterChip(
            selected = state.showLowStockOnly,
            onClick = { onLowStockToggled(!state.showLowStockOnly) },
            label = { Text(stringResource(R.string.inventory_low_stock)) },
            leadingIcon = if (state.showLowStockOnly) {
                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
            } else null
        )

        // Category dropdown
        Box {
            FilterChip(
                selected = state.selectedCategory != null,
                onClick = { showCategoryMenu = true },
                label = { Text(state.selectedCategory ?: stringResource(R.string.inventory_category)) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
            )
            DropdownMenu(
                expanded = showCategoryMenu,
                onDismissRequest = { showCategoryMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.inventory_all_categories)) },
                    onClick = {
                        onCategorySelected(null)
                        showCategoryMenu = false
                    }
                )
                state.categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category) },
                        onClick = {
                            onCategorySelected(category)
                            showCategoryMenu = false
                        }
                    )
                }
            }
        }

        // Location dropdown
        Box {
            FilterChip(
                selected = state.selectedLocation != null,
                onClick = { showLocationMenu = true },
                label = { Text(state.selectedLocation ?: stringResource(R.string.inventory_location)) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
            )
            DropdownMenu(
                expanded = showLocationMenu,
                onDismissRequest = { showLocationMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.inventory_all_locations)) },
                    onClick = {
                        onLocationSelected(null)
                        showLocationMenu = false
                    }
                )
                state.locations.forEach { location ->
                    DropdownMenuItem(
                        text = { Text(location) },
                        onClick = {
                            onLocationSelected(location)
                            showLocationMenu = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun InventoryItemCard(item: InventoryItem) {
    val containerColor = when {
        item.isLowStock -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        item.isExpiringSoon -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top row: item name + quantity badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.itemName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                QuantityBadge(quantity = item.quantity, isLow = item.isLowStock)
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Type + box label
            Text(
                text = listOfNotNull(item.itemType.ifBlank { null }, item.boxLabel.ifBlank { null }).joinToString(" - "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Info row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                InfoChip(icon = Icons.Default.LocationOn, text = "${item.room} - ${item.location}")
                InfoChip(icon = Icons.Default.Category, text = item.category)
            }

            // Expiration dates
            if (item.expirationDates.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (item.isExpiringSoon) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = item.expirationDates,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.isExpiringSoon) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

        }
    }
}

@Composable
private fun QuantityBadge(quantity: Int, isLow: Boolean) {
    Surface(
        color = if (isLow) MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
        else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = stringResource(R.string.inventory_qty, quantity),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (isLow) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun InfoChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}
