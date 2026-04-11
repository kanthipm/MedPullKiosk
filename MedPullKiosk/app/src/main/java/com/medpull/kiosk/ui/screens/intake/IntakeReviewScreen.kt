package com.medpull.kiosk.ui.screens.intake

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.medpull.kiosk.data.models.FieldType
import com.medpull.kiosk.data.models.FormField

/**
 * Review screen shown after guided intake completes.
 * Displays all collected data in editable text boxes, grouped by section.
 * Required unfilled fields are flagged in red.
 * Skipped fields are shown as "Not applicable."
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntakeReviewScreen(
    onNavigateBack: () -> Unit,
    onSubmit: () -> Unit,
    viewModel: IntakeReviewViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isSubmitted) {
        if (state.isSubmitted) onSubmit()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Review Your Answers",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            state.formName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Summary counts
                    val unfilled = state.fields.count {
                        it.required && it.id !in state.skippedFieldIds && it.value.isNullOrBlank()
                    }
                    if (unfilled > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "$unfilled required field${if (unfilled != 1) "s" else ""} still need${if (unfilled == 1) "s" else ""} an answer",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Button(
                        onClick = { viewModel.submit() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = !state.isLoading
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Looks good — submit",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Loading your answers...")
                    }
                }
            }
            state.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Error, null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            state.error!!,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            else -> {
                // Group fields by section (fields come in section order from schema)
                val sections = groupFieldsBySectionLabel(state.fields)

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sections.forEach { (sectionName, sectionFields) ->
                        item {
                            SectionHeader(sectionName)
                        }
                        items(sectionFields, key = { it.id }) { field ->
                            when {
                                field.fieldType == FieldType.STATIC_LABEL -> { /* skip labels */ }
                                field.id in state.skippedFieldIds -> {
                                    SkippedFieldRow(field)
                                }
                                else -> {
                                    ReviewFieldCard(
                                        field = field,
                                        onValueChange = { viewModel.updateField(field.id, it) }
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text = "  $title  ",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ReviewFieldCard(
    field: FormField,
    onValueChange: (String) -> Unit
) {
    val isEmpty = field.required && field.value.isNullOrBlank()
    val borderColor = if (isEmpty)
        MaterialTheme.colorScheme.error
    else
        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isEmpty) 1.5.dp else 0.5.dp,
            color = borderColor
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isEmpty)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = field.fieldName,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isEmpty)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f)
                )
                if (field.required) {
                    Text(
                        text = "Required",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = if (isEmpty)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }

            if (isEmpty) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Please fill in before submitting",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            OutlinedTextField(
                value = field.value ?: "",
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = if (isEmpty) "Tap to enter…" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = field.fieldType != FieldType.TEXT,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = if (isEmpty)
                        MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
            )
        }
    }
}

@Composable
private fun SkippedFieldRow(field: FormField) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = field.fieldName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "Not applicable",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }
}

/**
 * Groups fields into sections based on the schema order.
 * Uses known section boundary field IDs to infer section labels.
 */
private fun groupFieldsBySectionLabel(fields: List<FormField>): List<Pair<String, List<FormField>>> {
    val sections = mutableListOf<Pair<String, MutableList<FormField>>>()
    val sectionBoundaries = mapOf(
        "preferred_language" to "Registration",
        "medical_conditions" to "Health History",
        "hipaa_summary_delivered" to "HIPAA Consent",
        "general_consents_summary_delivered" to "General Consents"
    )

    var currentSection = "Registration"
    var currentFields = mutableListOf<FormField>()

    for (field in fields) {
        val newSection = sectionBoundaries[field.id]
        if (newSection != null && newSection != currentSection) {
            if (currentFields.isNotEmpty()) {
                sections.add(Pair(currentSection, currentFields))
            }
            currentSection = newSection
            currentFields = mutableListOf()
        }
        currentFields.add(field)
    }

    if (currentFields.isNotEmpty()) {
        sections.add(Pair(currentSection, currentFields))
    }

    return sections
}
