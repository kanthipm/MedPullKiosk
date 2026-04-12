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
 * AI chat service backed by Grok (xAI) — OpenAI-compatible API.
 */
@Singleton
class GrokApiService @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "GrokApiService"
    }

    /**
     * Send a chat message with full conversation history for multi-turn context.
     * System prompt is inserted as the first message with role="system".
     */
    suspend fun sendMessage(
        userMessage: String,
        conversationHistory: List<ChatMessage> = emptyList(),
        systemPrompt: String? = null,
        model: String = Constants.AI.GROK_MODEL,
        maxTokens: Int = Constants.AI.MAX_TOKENS
    ): AiResponse = withContext(Dispatchers.IO) {
        try {
            val messages = mutableListOf<GrokMessage>()

            // System prompt goes as the first message
            if (!systemPrompt.isNullOrBlank()) {
                messages.add(GrokMessage(role = "system", content = systemPrompt))
            }

            // Conversation history
            for (msg in conversationHistory) {
                messages.add(
                    GrokMessage(
                        role = if (msg.isFromUser) "user" else "assistant",
                        content = msg.text
                    )
                )
            }

            // Current user message
            messages.add(GrokMessage(role = "user", content = userMessage))

            val requestBody = GrokRequest(
                model = model,
                maxTokens = maxTokens,
                messages = messages
            )

            val bodyJson = gson.toJson(requestBody)

            val request = Request.Builder()
                .url(Constants.AI.GROK_API_URL)
                .addHeader("Authorization", "Bearer ${BuildConfig.GROK_API_KEY}")
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val responseJson = response.body?.string()
                if (responseJson != null) {
                    val grokResponse = gson.fromJson(responseJson, GrokResponse::class.java)
                    val text = grokResponse.choices?.firstOrNull()?.message?.content
                    if (!text.isNullOrBlank()) {
                        Log.d(TAG, "Grok response received (${text.length} chars)")
                        AiResponse.Success(text)
                    } else {
                        AiResponse.Error("Empty response from AI")
                    }
                } else {
                    AiResponse.Error("Empty response body")
                }
            } else {
                val errorBody = response.body?.string()
                Log.e(TAG, "Grok API error: ${response.code} - $errorBody")
                when (response.code) {
                    401 -> AiResponse.Error("Invalid API key. Check your Grok API key.")
                    429 -> AiResponse.Error("Rate limit exceeded. Please try again shortly.")
                    else -> AiResponse.Error("AI request failed (${response.code})")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Grok API", e)
            AiResponse.Error("Failed to connect to AI: ${e.message}")
        }
    }

    /**
     * Get field suggestion (used by AI assistant screen) — uses mini model, no PHI in prompt
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
        return sendMessage(
            prompt,
            systemPrompt = buildSystemPrompt(language, null),
            model = Constants.AI.CHAT_ASSISTANT_MODEL,
            maxTokens = Constants.AI.CHAT_MAX_TOKENS
        )
    }

    /**
     * Explain medical term (used by AI assistant screen) — uses mini model, no PHI in prompt
     */
    suspend fun explainMedicalTerm(
        term: String,
        language: String = "en"
    ): AiResponse {
        val prompt = "What does the medical term '$term' mean? Explain in simple terms."
        return sendMessage(
            prompt,
            systemPrompt = buildSystemPrompt(language, null),
            model = Constants.AI.CHAT_ASSISTANT_MODEL,
            maxTokens = Constants.AI.CHAT_MAX_TOKENS
        )
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

        return if (context != null) "$base\n\nCurrent form context:\n$context" else base
    }
}

// --- Grok (OpenAI-compatible) request/response models ---

data class GrokRequest(
    val model: String,
    @SerializedName("max_tokens")
    val maxTokens: Int,
    val messages: List<GrokMessage>
)

data class GrokMessage(
    val role: String,
    val content: String
)

data class GrokResponse(
    val id: String?,
    val choices: List<GrokChoice>?,
    val usage: GrokUsage?
)

data class GrokChoice(
    val index: Int?,
    val message: GrokMessage?,
    @SerializedName("finish_reason")
    val finishReason: String?
)

data class GrokUsage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int?,
    @SerializedName("completion_tokens")
    val completionTokens: Int?,
    @SerializedName("total_tokens")
    val totalTokens: Int?
)

