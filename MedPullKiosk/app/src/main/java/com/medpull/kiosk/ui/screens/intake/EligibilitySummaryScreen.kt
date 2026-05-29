package com.medpull.kiosk.ui.screens.intake

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.medpull.kiosk.R
import com.medpull.kiosk.data.models.DocumentType
import com.medpull.kiosk.data.models.FormField
import com.medpull.kiosk.data.models.UploadStatus
import java.io.File

// Logical section groupings for the summary display
private val ELIGIBILITY_SECTIONS = listOf(
    "section_personal_information" to listOf("full_name", "date_of_birth"),
    "section_address" to listOf("address_street", "address_city", "address_state", "address_zip"),
    "section_household" to listOf("household_size", "number_of_dependents"),
    "section_income" to listOf("income_sources", "monthly_income"),
    "section_employment_insurance" to listOf("employment_status", "insurance_status")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EligibilitySummaryScreen(
    onEditInfo: () -> Unit,
    onReuploadDocs: () -> Unit,
    onSubmitted: () -> Unit,
    onBack: () -> Unit = {},
    viewModel: EligibilitySummaryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isSubmitted) {
        if (state.isSubmitted) onSubmitted()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.eligibility_summary), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.review_before_submitting),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            EligibilityActionBar(
                state = state,
                onEditInfo = onEditInfo,
                onReuploadDocs = onReuploadDocs,
                onSubmit = viewModel::submit
            )
        }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            }
            else -> {
                val fieldMap = state.fields.associateBy { it.id }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Validation banner
                    if (!state.isReadyToSubmit) {
                        item { ValidationBanner(state) }
                    }

                    // Patient info sections
                    ELIGIBILITY_SECTIONS.forEach { (sectionKey, fieldIds) ->
                        val sectionFields = fieldIds.mapNotNull { fieldMap[it] }
                        if (sectionFields.isNotEmpty()) {
                            item { SummarySectionHeader(sectionKey) }
                            item { SummarySectionCard(sectionFields, state.skippedFieldIds) }
                        }
                    }

                    // Documents section
                    item { SummarySectionHeader("section_supporting_documents") }
                    item { DocumentsSectionCard(state.documents) }

                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

// ── Validation banner ──────────────────────────────────────────────────────

@Composable
private fun ValidationBanner(state: EligibilitySummaryState) {
    val missingFields = state.missingRequiredFields
    val missingDocs = state.missingRequiredDocs
    if (missingFields.isEmpty() && missingDocs.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning, null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.incomplete_review),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            if (missingFields.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(
                        R.string.missing_fields_list,
                        missingFields.joinToString(", ") { it.fieldName }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            if (missingDocs.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(
                        R.string.missing_documents_list,
                        missingDocs.map { it.localizedDisplayName() }.joinToString(", ")
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

// ── Section header ─────────────────────────────────────────────────────────

@Composable
private fun SummarySectionHeader(sectionKey: String) {
    val title = localizedSectionTitle(sectionKey)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
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

// ── Patient info card ──────────────────────────────────────────────────────

@Composable
private fun SummarySectionCard(fields: List<FormField>, skippedIds: Set<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            fields.forEachIndexed { index, field ->
                SummaryFieldRow(field, isSkipped = field.id in skippedIds)
                if (index < fields.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryFieldRow(field: FormField, isSkipped: Boolean) {
    val isEmpty = field.required && !isSkipped && field.value.isNullOrBlank()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = field.fieldName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.weight(0.45f)
        )
        Spacer(Modifier.width(8.dp))
        Row(
            modifier = Modifier.weight(0.55f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isEmpty) {
                Icon(
                    Icons.Default.Error, null,
                    modifier = Modifier.size(14.dp).padding(end = 4.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    stringResource(R.string.missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
            } else if (isSkipped) {
                Text(
                    stringResource(R.string.review_not_applicable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            } else {
                Text(
                    text = field.value ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

// ── Documents card ─────────────────────────────────────────────────────────

@Composable
private fun DocumentsSectionCard(documents: List<DocumentSlot>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (documents.isEmpty()) {
                Text(
                    stringResource(R.string.no_documents_uploaded),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            } else {
                documents.forEachIndexed { index, slot ->
                    SummaryDocumentRow(slot)
                    if (index < documents.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryDocumentRow(slot: DocumentSlot) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Thumbnail / icon
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            when {
                slot.status == UploadStatus.UPLOADED && slot.filePath != null -> {
                    if (slot.filePath.endsWith(".pdf", ignoreCase = true)) {
                        Icon(
                            Icons.Default.Description, null,
                            modifier = Modifier.size(22.dp),
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
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                else ->
                    Icon(Icons.Default.Warning, null,
                        modifier = Modifier.size(20.dp),
                        tint = if (slot.type.required) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }

        // Name + status
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    slot.type.localizedDisplayName(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (!slot.type.required) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.optional_paren),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
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
                    UploadStatus.SKIPPED -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    UploadStatus.MISSING -> if (slot.type.required) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                }
            )
        }

        // Right-side status icon
        when (slot.status) {
            UploadStatus.UPLOADED ->
                Icon(Icons.Default.CheckCircle, null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary)
            UploadStatus.MISSING ->
                if (slot.type.required)
                    Icon(Icons.Default.Error, null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error)
            else -> {}
        }
    }
}

// ── Action bar ─────────────────────────────────────────────────────────────

@Composable
private fun EligibilityActionBar(
    state: EligibilitySummaryState,
    onEditInfo: () -> Unit,
    onReuploadDocs: () -> Unit,
    onSubmit: () -> Unit
) {
    Surface(tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (!state.isReadyToSubmit) {
                val missingCount = state.missingRequiredFields.size + state.missingRequiredDocs.size
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    Icon(
                        Icons.Default.Warning, null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.items_need_attention, missingCount),
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
                    onClick = onEditInfo,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.edit_info), style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = onReuploadDocs,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.Upload, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.documents), style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = onSubmit,
                    modifier = Modifier.weight(1f).height(48.dp),
                    enabled = state.isReadyToSubmit
                ) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.review_submit), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
