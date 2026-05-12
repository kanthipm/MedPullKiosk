package com.medpull.kiosk.ui.screens.intake

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medpull.kiosk.data.local.entities.DocumentEntity
import com.medpull.kiosk.data.models.DocumentType
import com.medpull.kiosk.data.models.UploadStatus
import com.medpull.kiosk.data.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class DocumentSlot(
    val type: DocumentType,
    val status: UploadStatus = UploadStatus.MISSING,
    val filePath: String? = null
)

data class DocumentUploadState(
    val sessionId: String = "",
    val slots: Map<DocumentType, DocumentSlot> = DocumentType.values().associateWith { DocumentSlot(it) },
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val canProceed: Boolean
        get() = DocumentType.values()
            .filter { it.required }
            .all { type ->
                val slot = slots[type] ?: return@all false
                slot.status == UploadStatus.UPLOADED || slot.status == UploadStatus.SKIPPED
            }
}

@HiltViewModel
class DocumentUploadViewModel @Inject constructor(
    private val repository: DocumentRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(DocumentUploadState())
    val state: StateFlow<DocumentUploadState> = _state.asStateFlow()

    init {
        val sessionId = savedStateHandle.get<String>("formId") ?: ""
        _state.update { it.copy(sessionId = sessionId) }
        loadExistingDocuments(sessionId)
    }

    private fun loadExistingDocuments(sessionId: String) {
        viewModelScope.launch {
            repository.getDocumentsFlow(sessionId).collect { entities ->
                val updated = DocumentType.values().associateWith { type ->
                    val entity = entities.find { it.documentType == type.name }
                    if (entity != null) {
                        DocumentSlot(
                            type = type,
                            status = entity.uploadStatus(),
                            filePath = entity.filePath
                        )
                    } else {
                        DocumentSlot(type = type)
                    }
                }
                _state.update { it.copy(slots = updated) }
            }
        }
    }

    fun onFileSelected(type: DocumentType, uri: Uri) {
        val sessionId = _state.value.sessionId
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching {
                repository.saveDocument(sessionId, type, uri)
            }.onSuccess { entity ->
                updateSlot(entity)
            }.onFailure { e ->
                _state.update { it.copy(error = "Failed to save document: ${e.message}") }
            }
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun onPhotoCaptured(type: DocumentType, tempFile: File) {
        val sessionId = _state.value.sessionId
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching {
                repository.confirmCameraCapture(sessionId, type, tempFile)
            }.onSuccess { entity ->
                updateSlot(entity)
            }.onFailure { e ->
                _state.update { it.copy(error = "Failed to save photo: ${e.message}") }
            }
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun skipDocument(type: DocumentType) {
        val sessionId = _state.value.sessionId
        viewModelScope.launch {
            runCatching {
                repository.skipDocument(sessionId, type)
            }.onSuccess {
                _state.update { state ->
                    state.copy(
                        slots = state.slots + (type to DocumentSlot(type, UploadStatus.SKIPPED, null))
                    )
                }
            }
        }
    }

    fun deleteDocument(type: DocumentType) {
        val sessionId = _state.value.sessionId
        viewModelScope.launch {
            runCatching {
                repository.deleteDocument(sessionId, type)
            }.onSuccess {
                _state.update { state ->
                    state.copy(
                        slots = state.slots + (type to DocumentSlot(type, UploadStatus.MISSING, null))
                    )
                }
            }
        }
    }

    suspend fun createTempCameraFile(type: DocumentType): File =
        repository.createTempCameraFile(_state.value.sessionId, type)

    private fun updateSlot(entity: DocumentEntity) {
        val type = entity.documentType()
        _state.update { state ->
            state.copy(
                slots = state.slots + (type to DocumentSlot(
                    type = type,
                    status = entity.uploadStatus(),
                    filePath = entity.filePath
                ))
            )
        }
    }
}
