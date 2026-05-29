package com.medpull.kiosk.ui.screens.ai

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medpull.kiosk.data.models.FormField
import com.medpull.kiosk.R
import com.medpull.kiosk.data.repository.AiChatResult
import com.medpull.kiosk.data.repository.AiRepository
import com.medpull.kiosk.utils.AppStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for AI assistance
 */
@HiltViewModel
class AiAssistanceViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val appStrings: AppStrings
) : ViewModel() {

    companion object {
        private const val TAG = "AiAssistanceViewModel"
    }

    private val _state = MutableStateFlow(AiAssistanceState())
    val state: StateFlow<AiAssistanceState> = _state.asStateFlow()

    /**
     * Send a chat message
     */
    fun sendMessage(message: String) {
        if (message.isBlank()) return

        viewModelScope.launch {
            try {
                // Add user message to chat
                val userMessage = ChatMessage(
                    text = message,
                    isFromUser = true,
                    timestamp = System.currentTimeMillis()
                )
                _state.update {
                    it.copy(
                        messages = it.messages + userMessage,
                        isLoading = true,
                        error = null
                    )
                }

                Log.d(TAG, "Sending message to AI: $message")

                // Build context from form name + fields summary
                val fullContext = buildString {
                    _state.value.formContext?.let { append("Form: $it\n") }
                    _state.value.formFieldsSummary?.let { append("Fields:\n$it") }
                }.takeIf { it.isNotBlank() }

                // Get AI response with conversation history for multi-turn context
                when (val result = aiRepository.sendChatMessage(
                    message = message,
                    language = _state.value.language,
                    formContext = fullContext,
                    conversationHistory = _state.value.messages
                )) {
                    is AiChatResult.Success -> {
                        Log.d(TAG, "AI response received: ${result.message}")
                        val aiMessage = ChatMessage(
                            text = result.message,
                            isFromUser = false,
                            timestamp = System.currentTimeMillis()
                        )
                        _state.update {
                            it.copy(
                                messages = it.messages + aiMessage,
                                isLoading = false
                            )
                        }
                    }
                    is AiChatResult.Error -> {
                        Log.e(TAG, "AI error: ${result.message}")
                        _state.update {
                            it.copy(
                                error = result.message,
                                isLoading = false
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message", e)
                _state.update {
                    it.copy(
                        error = appStrings.get(R.string.err_failed_send_message, e.message ?: ""),
                        isLoading = false
                    )
                }
            }
        }
    }

    /**
     * Get help for a specific field
     */
    fun getFieldHelp(field: FormField) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true, error = null) }

                when (val result = aiRepository.getFieldHelp(field, _state.value.language)) {
                    is AiChatResult.Success -> {
                        val aiMessage = ChatMessage(
                            text = result.message,
                            isFromUser = false,
                            timestamp = System.currentTimeMillis()
                        )
                        _state.update {
                            it.copy(
                                messages = it.messages + aiMessage,
                                isLoading = false
                            )
                        }
                    }
                    is AiChatResult.Error -> {
                        _state.update {
                            it.copy(
                                error = result.message,
                                isLoading = false
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        error = appStrings.get(R.string.err_failed_field_help, e.message ?: ""),
                        isLoading = false
                    )
                }
            }
        }
    }

    /**
     * Suggest a value for a field
     */
    fun suggestFieldValue(field: FormField) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true, error = null) }

                when (val result = aiRepository.suggestFieldValue(field, _state.value.language)) {
                    is AiChatResult.Success -> {
                        val aiMessage = ChatMessage(
                            text = result.message,
                            isFromUser = false,
                            timestamp = System.currentTimeMillis()
                        )
                        _state.update {
                            it.copy(
                                messages = it.messages + aiMessage,
                                isLoading = false
                            )
                        }
                    }
                    is AiChatResult.Error -> {
                        _state.update {
                            it.copy(
                                error = result.message,
                                isLoading = false
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        error = appStrings.get(R.string.err_failed_suggest_value, e.message ?: ""),
                        isLoading = false
                    )
                }
            }
        }
    }

    /**
     * Explain a medical term
     */
    fun explainTerm(term: String) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true, error = null) }

                when (val result = aiRepository.explainTerm(term, _state.value.language)) {
                    is AiChatResult.Success -> {
                        val aiMessage = ChatMessage(
                            text = result.message,
                            isFromUser = false,
                            timestamp = System.currentTimeMillis()
                        )
                        _state.update {
                            it.copy(
                                messages = it.messages + aiMessage,
                                isLoading = false
                            )
                        }
                    }
                    is AiChatResult.Error -> {
                        _state.update {
                            it.copy(
                                error = result.message,
                                isLoading = false
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        error = appStrings.get(R.string.err_failed_explain_term, e.message ?: ""),
                        isLoading = false
                    )
                }
            }
        }
    }

    /**
     * Set language
     */
    fun setLanguage(language: String) {
        _state.update { it.copy(language = language) }
    }

    /**
     * Set form context — includes form name and a summary of fields
     */
    fun setFormContext(context: String) {
        _state.update { it.copy(formContext = context) }
    }

    /**
     * Set form fields context so the AI knows what fields are on the form
     */
    fun setFormFields(fields: List<FormField>) {
        if (fields.isEmpty()) return
        val summary = fields.joinToString("\n") { field ->
            val status = if (!field.value.isNullOrBlank()) "filled: ${field.value}" else "empty"
            "- ${field.fieldName} (${field.fieldType.name}): $status"
        }
        _state.update { it.copy(formFieldsSummary = summary) }
    }

    /**
     * Clear error
     */
    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * Clear chat history
     */
    fun clearChat() {
        _state.update { it.copy(messages = emptyList()) }
    }
}

/**
 * AI assistance UI state
 */
data class AiAssistanceState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val language: String = "en",
    val formContext: String? = null,
    val formFieldsSummary: String? = null
)

/**
 * Chat message data class
 */
data class ChatMessage(
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long,
    val isClarification: Boolean = false  // true = sidebar Q&A, never shown as main question
)
