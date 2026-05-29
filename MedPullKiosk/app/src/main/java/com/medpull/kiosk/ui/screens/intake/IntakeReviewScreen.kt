package com.medpull.kiosk.ui.screens.intake

import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.medpull.kiosk.R
import com.medpull.kiosk.data.models.DocumentType
import com.medpull.kiosk.data.models.FieldType
import com.medpull.kiosk.data.models.FormField
import com.medpull.kiosk.data.models.UploadStatus
import java.io.File

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
    val context = LocalContext.current

    LaunchedEffect(state.isSubmitted) {
        if (state.isSubmitted) onSubmit()
    }

    // Launch share sheet when PDF is ready
    LaunchedEffect(state.pdfFile) {
        val file = state.pdfFile ?: return@LaunchedEffect
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, state.formName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(intent, context.getString(R.string.review_share_or_print))
            )
        } catch (e: Exception) {
            android.util.Log.e("IntakeReviewScreen", "Error sharing PDF", e)
        }
        viewModel.clearPdfFile()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.review_title),
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
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
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
                                text = stringResource(R.string.review_unfilled_fields, unfilled),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val dir = java.io.File(context.filesDir, "pdf_exports")
                                viewModel.generatePdf(dir)
                            },
                            modifier = Modifier.weight(1f).height(52.dp),
                            enabled = !state.isLoading && !state.isGeneratingPdf
                        ) {
                            if (state.isGeneratingPdf) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (state.isGeneratingPdf) {
                                    stringResource(R.string.review_generating)
                                } else {
                                    stringResource(R.string.export_pdf)
                                },
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Button(
                            onClick = { viewModel.submit() },
                            modifier = Modifier.weight(1f).height(52.dp),
                            enabled = !state.isLoading
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.review_submit),
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
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
                        Text(stringResource(R.string.review_loading))
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
                val sections = groupFieldsBySectionLabel(state.fields, state.formId)

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sections.forEach { (sectionKey, sectionFields) ->
                        item {
                            SectionHeader(sectionKey)
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

                    // Documents section — only shown for Sliding Fee
                    if (state.documents.isNotEmpty()) {
                        item { SectionHeader("section_supporting_documents") }
                        items(state.documents, key = { "doc_${it.type.name}" }) { slot ->
                            DocumentReviewRow(slot)
                        }
                    }

                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(sectionKey: String) {
    val title = localizedSectionTitle(sectionKey)
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
                        text = stringResource(R.string.review_required_label),
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
                        stringResource(R.string.review_required_field_warning),
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
                        text = if (isEmpty) stringResource(R.string.review_tap_to_enter) else "",
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
            text = stringResource(R.string.review_not_applicable),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }
}

// ── Document review row ────────────────────────────────────────────────────

@Composable
private fun DocumentReviewRow(slot: DocumentSlot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (slot.status == UploadStatus.MISSING) 1.5.dp else 0.5.dp,
            color = when (slot.status) {
                UploadStatus.UPLOADED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                UploadStatus.MISSING -> MaterialTheme.colorScheme.error
                UploadStatus.SKIPPED -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            }
        ),
        colors = CardDefaults.cardColors(
            containerColor = when (slot.status) {
                UploadStatus.MISSING -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail / status icon
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                when {
                    slot.status == UploadStatus.UPLOADED && slot.filePath != null -> {
                        val isPdf = slot.filePath.endsWith(".pdf", ignoreCase = true)
                        if (isPdf) {
                            Icon(
                                Icons.Default.Description, null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            AsyncImage(
                                model = File(slot.filePath),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    slot.status == UploadStatus.SKIPPED ->
                        Icon(Icons.Default.RemoveCircleOutline, null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    else ->
                        Icon(Icons.Default.Warning, null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.error)
                }
            }

            // Label + status text
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = slot.type.localizedDisplayName(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    if (!slot.type.required) {
                        Text(
                            stringResource(R.string.optional_label),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when (slot.status) {
                        UploadStatus.UPLOADED -> stringResource(R.string.status_uploaded)
                        UploadStatus.SKIPPED -> stringResource(R.string.status_skipped)
                        UploadStatus.MISSING -> if (slot.type.required) {
                            stringResource(R.string.status_missing_required)
                        } else {
                            stringResource(R.string.status_not_provided)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when (slot.status) {
                        UploadStatus.UPLOADED -> MaterialTheme.colorScheme.primary
                        UploadStatus.SKIPPED -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        UploadStatus.MISSING -> if (slot.type.required) MaterialTheme.colorScheme.error
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    }
                )
            }

            // Status icon on the right
            when (slot.status) {
                UploadStatus.UPLOADED ->
                    Icon(Icons.Default.CheckCircle, null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary)
                UploadStatus.MISSING ->
                    if (slot.type.required)
                        Icon(Icons.Default.Error, null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error)
                else -> {}
            }
        }
    }
}

// ── Field grouping ─────────────────────────────────────────────────────────

/** Per-form explicit field → section key maps for correct display order. */
private val FIELD_SECTION_MAPS = mapOf(
    "sliding_fee_intake" to linkedMapOf(
        "full_name" to "section_personal_information",
        "date_of_birth" to "section_personal_information",
        "address_street" to "section_address",
        "address_city" to "section_address",
        "address_state" to "section_address",
        "address_zip" to "section_address",
        "household_size" to "section_household",
        "number_of_dependents" to "section_household",
        "income_sources" to "section_income",
        "monthly_income" to "section_income",
        "employment_status" to "section_employment_insurance",
        "insurance_status" to "section_employment_insurance"
    )
)

@Composable
internal fun localizedSectionTitle(key: String): String = when (key) {
    "section_personal_information" -> stringResource(R.string.section_personal_information)
    "section_address" -> stringResource(R.string.section_address)
    "section_household" -> stringResource(R.string.section_household)
    "section_income" -> stringResource(R.string.section_income)
    "section_employment_insurance" -> stringResource(R.string.section_employment_insurance)
    "section_registration" -> stringResource(R.string.section_registration)
    "section_health_history" -> stringResource(R.string.section_health_history)
    "section_hipaa_consent" -> stringResource(R.string.section_hipaa_consent)
    "section_general_consents" -> stringResource(R.string.section_general_consents)
    "section_supporting_documents" -> stringResource(R.string.section_supporting_documents)
    "section_other" -> stringResource(R.string.section_other)
    else -> key
}

/**
 * Groups fields into labelled sections.
 * Uses an explicit field→section map for known forms; falls back to
 * boundary-based detection for others (e.g. Coastal Gateway).
 */
private fun groupFieldsBySectionLabel(
    fields: List<FormField>,
    formId: String
): List<Pair<String, List<FormField>>> {
    val explicitMap = FIELD_SECTION_MAPS[formId]
    if (explicitMap != null) {
        val grouped = LinkedHashMap<String, MutableList<FormField>>()
        for (field in fields) {
            val section = explicitMap[field.id] ?: "section_other"
            grouped.getOrPut(section) { mutableListOf() }.add(field)
        }
        return grouped.entries.map { Pair(it.key, it.value.toList()) }
    }

    // Fallback: boundary-based detection for Coastal Gateway / Medicaid
    val sections = mutableListOf<Pair<String, MutableList<FormField>>>()
    val sectionBoundaries = mapOf(
        "preferred_language" to "section_registration",
        "medical_conditions" to "section_health_history",
        "hipaa_summary_delivered" to "section_hipaa_consent",
        "general_consents_summary_delivered" to "section_general_consents"
    )
    var currentSection = "section_registration"
    var currentFields = mutableListOf<FormField>()
    for (field in fields) {
        val newSection = sectionBoundaries[field.id]
        if (newSection != null && newSection != currentSection) {
            if (currentFields.isNotEmpty()) sections.add(Pair(currentSection, currentFields))
            currentSection = newSection
            currentFields = mutableListOf()
        }
        currentFields.add(field)
    }
    if (currentFields.isNotEmpty()) sections.add(Pair(currentSection, currentFields))
    return sections
}
