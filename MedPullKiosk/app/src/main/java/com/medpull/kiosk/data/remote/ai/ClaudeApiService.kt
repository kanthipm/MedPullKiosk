package com.medpull.kiosk.data.remote.ai

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.medpull.kiosk.BuildConfig
import com.medpull.kiosk.ui.screens.ai.ChatMessage
import com.medpull.kiosk.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Claude API service for AI assistance.
 * Calls the Anthropic Messages API directly with an API key.
 */
@Singleton
class ClaudeApiService @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "ClaudeApiService"
    }

    /**
     * Send a chat message to Claude with full conversation history for multi-turn context.
     */
    suspend fun sendMessage(
        userMessage: String,
        conversationHistory: List<ChatMessage> = emptyList(),
        systemPrompt: String? = null,
        model: String = Constants.AI.CLAUDE_MODEL,
        maxTokens: Int = Constants.AI.MAX_TOKENS
    ): AiResponse = withContext(Dispatchers.IO) {
        try {
            // Build messages list from conversation history + new user message
            val messages = mutableListOf<ClaudeMessage>()
            for (msg in conversationHistory) {
                messages.add(
                    ClaudeMessage(
                        role = if (msg.isFromUser) "user" else "assistant",
                        content = msg.text
                    )
                )
            }
            // Add the current user message
            messages.add(ClaudeMessage(role = "user", content = userMessage))

            val requestBody = ClaudeRequest(
                model = model,
                maxTokens = maxTokens,
                system = systemPrompt,
                messages = messages
            )

            val bodyJson = gson.toJson(requestBody)

            val request = Request.Builder()
                .url(Constants.AI.CLAUDE_API_URL)
                .addHeader("x-api-key", BuildConfig.CLAUDE_API_KEY)
                .addHeader("anthropic-version", Constants.AI.CLAUDE_API_VERSION)
                .addHeader("content-type", "application/json")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val responseJson = response.body?.string()
                if (responseJson != null) {
                    val claudeResponse = gson.fromJson(responseJson, ClaudeResponse::class.java)
                    val text = claudeResponse.content?.firstOrNull()?.text
                    if (!text.isNullOrBlank()) {
                        Log.d(TAG, "Claude response received (${text.length} chars)")
                        AiResponse.Success(text)
                    } else {
                        AiResponse.Error("Empty response from AI")
                    }
                } else {
                    AiResponse.Error("Empty response body")
                }
            } else {
                val errorBody = response.body?.string()
                Log.e(TAG, "Claude API error: ${response.code} - $errorBody")
                when (response.code) {
                    401 -> AiResponse.Error("Invalid API key. Check your Claude API key.")
                    429 -> AiResponse.Error("Rate limit exceeded. Please try again shortly.")
                    529 -> AiResponse.Error("Claude is temporarily overloaded. Please try again.")
                    else -> AiResponse.Error("AI request failed (${response.code})")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Claude API", e)
            AiResponse.Error("Failed to connect to AI: ${e.message}")
        }
    }

    /**
     * Get field suggestion
     */
    suspend fun suggestFieldValue(
        fieldName: String,
        fieldType: String,
        language: String = "en"
    ): AiResponse {
        val prompt = """
            For a medical form field named "$fieldName" of type $fieldType,
            suggest an appropriate example value and explain what should go in this field.
        """.trimIndent()
        return sendMessage(prompt, systemPrompt = buildSystemPrompt(language, null))
    }

    /**
     * Explain medical term
     */
    suspend fun explainMedicalTerm(
        term: String,
        language: String = "en"
    ): AiResponse {
        val prompt = "What does the medical term '$term' mean? Explain in simple terms."
        return sendMessage(prompt, systemPrompt = buildSystemPrompt(language, null))
    }

    fun buildSystemPrompt(language: String, context: String?): String {
        val languageName = when (language) {
            "es" -> "Spanish"
            "zh" -> "Chinese"
            "fr" -> "French"
            "hi" -> "Hindi"
            "ar" -> "Arabic"
            else -> "English"
        }

        val base = """
            You are Mira, a friendly and helpful medical form assistant on a patient kiosk. Your role is to:
            1. Help users understand medical form fields and what information is being asked for
            2. Suggest appropriate values for form fields (e.g. date formats, common entries)
            3. Explain medical terminology in simple, easy-to-understand terms
            4. Answer questions about the form they are filling out
            5. Provide all guidance in $languageName

            Keep responses concise (2-3 sentences max). Always respond in $languageName.
            Never provide medical advice or diagnoses. Only help with form-filling questions.
        """.trimIndent()

        return if (context != null) {
            "$base\n\nCurrent form context:\n$context"
        } else {
            base
        }
    }
}

// --- Claude API request/response models ---

data class ClaudeRequest(
    val model: String,
    @SerializedName("max_tokens")
    val maxTokens: Int,
    val system: String? = null,
    val messages: List<ClaudeMessage>
)

data class ClaudeMessage(
    val role: String,
    val content: String
)

data class ClaudeResponse(
    val id: String?,
    val content: List<ClaudeContent>?,
    @SerializedName("stop_reason")
    val stopReason: String?,
    val usage: ClaudeUsage?
)

data class ClaudeContent(
    val type: String?,
    val text: String?
)

data class ClaudeUsage(
    @SerializedName("input_tokens")
    val inputTokens: Int?,
    @SerializedName("output_tokens")
    val outputTokens: Int?
)
