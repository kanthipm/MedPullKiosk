package com.medpull.kiosk.healthcare.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.medpull.kiosk.R
import com.medpull.kiosk.healthcare.client.FhirAuthType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FhirSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: FhirSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) {
            viewModel.clearSaveSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.fhir_server_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Server URL
            OutlinedTextField(
                value = state.serverUrl,
                onValueChange = { viewModel.updateServerUrl(it) },
                label = { Text(stringResource(R.string.fhir_server_url)) },
                placeholder = { Text(stringResource(R.string.fhir_server_url_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) }
            )

            // Auth Type
            var authExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = authExpanded,
                onExpandedChange = { authExpanded = it }
            ) {
                OutlinedTextField(
                    value = state.authType.name.replace("_", " "),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.auth_type)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = authExpanded) }
                )
                ExposedDropdownMenu(
                    expanded = authExpanded,
                    onDismissRequest = { authExpanded = false }
                ) {
                    FhirAuthType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.name.replace("_", " ")) },
                            onClick = {
                                viewModel.updateAuthType(type)
                                authExpanded = false
                            }
                        )
                    }
                }
            }

            // SMART Client ID (visible only for SMART auth)
            if (state.authType == FhirAuthType.SMART_ON_FHIR) {
                OutlinedTextField(
                    value = state.smartClientId,
                    onValueChange = { viewModel.updateSmartClientId(it) },
                    label = { Text(stringResource(R.string.smart_client_id)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Save
                Button(
                    onClick = { viewModel.saveConfig() },
                    modifier = Modifier.weight(1f),
                    enabled = state.serverUrl.isNotBlank()
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.save))
                }

                // Test Connection
                OutlinedButton(
                    onClick = { viewModel.testConnection() },
                    modifier = Modifier.weight(1f),
                    enabled = state.serverUrl.isNotBlank() && !state.isTesting
                ) {
                    if (state.isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.NetworkCheck, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.test_connection))
                }
            }

            // Connection Status
            state.connectionStatus?.let { status ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (status == ConnectionStatus.SUCCESS) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (status == ConnectionStatus.SUCCESS) {
                                Icons.Default.CheckCircle
                            } else {
                                Icons.Default.Error
                            },
                            contentDescription = null,
                            tint = if (status == ConnectionStatus.SUCCESS) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (status == ConnectionStatus.SUCCESS) {
                                    stringResource(R.string.connection_successful)
                                } else {
                                    stringResource(R.string.connection_failed)
                                },
                                style = MaterialTheme.typography.titleSmall
                            )
                            if (status == ConnectionStatus.FAILED && state.connectionError != null) {
                                Text(
                                    text = state.connectionError!!,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
