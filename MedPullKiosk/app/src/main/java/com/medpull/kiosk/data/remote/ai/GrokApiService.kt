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
 * AI chat service backed by Grok (xAI) with automatic fallback to Groq (groq.com).
 *
 * Both use the identical OpenAI-compatible API format — only the base URL and key differ.
 * On any 429 (quota) or 401 (invalid key) from Grok, the request is transparently
 * retried against Groq's free tier (llama-3.3-70b-versatile, 14,400 req/day free).
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
     * Send a chat message. Tries Grok first; falls back to Groq on quota/auth errors.
     *
     * Set [preferGroq] to skip Grok entirely and call Groq's free tier directly
     * (when a Groq key is configured). Used by cost-sensitive code paths — e.g.
     * the intake engine — to conserve xAI credits.
     */
    suspend fun sendMessage(
        userMessage: String,
        conversationHistory: List<ChatMessage> = emptyList(),
        systemPrompt: String? = null,
        model: String = Constants.AI.GROK_MODEL,
        maxTokens: Int = Constants.AI.MAX_TOKENS,
        preferGroq: Boolean = false
    ): AiResponse = withContext(Dispatchers.IO) {
        val messages = buildMessages(systemPrompt, conversationHistory, userMessage)

        // Conserve xAI credits: when the caller prefers Groq and a key is set,
        // use Groq's free tier directly without touching Grok.
        if (preferGroq && BuildConfig.GROQ_API_KEY.isNotBlank()) {
            return@withContext callApi(
                url = Constants.AI.GROQ_API_URL,
                apiKey = BuildConfig.GROQ_API_KEY,
                model = Constants.AI.GROQ_MODEL,
                messages = messages,
                maxTokens = maxTokens
            )
        }

        // Try Grok first
        val grokResult = callApi(
            url = Constants.AI.GROK_API_URL,
            apiKey = BuildConfig.GROK_API_KEY,
            model = model,
            messages = messages,
            maxTokens = maxTokens
        )

        // Fall back to Groq on quota/auth failure (or if Grok key is blank)
        if (grokResult is AiResponse.Error && BuildConfig.GROQ_API_KEY.isNotBlank()) {
            val errMsg = grokResult.message
            val shouldFallback = errMsg.contains("429") || errMsg.contains("401") ||
                errMsg.contains("Rate limit") || errMsg.contains("Invalid API key") ||
                errMsg.contains("quota") || BuildConfig.GROK_API_KEY.isBlank()
            if (shouldFallback) {
                Log.i(TAG, "Grok unavailable ($errMsg) — falling back to Groq")
                return@withContext callApi(
                    url = Constants.AI.GROQ_API_URL,
                    apiKey = BuildConfig.GROQ_API_KEY,
                    model = Constants.AI.GROQ_MODEL,
                    messages = messages,
                    maxTokens = maxTokens
                )
            }
        }

        grokResult
    }

    private fun buildMessages(
        systemPrompt: String?,
        history: List<ChatMessage>,
        userMessage: String
    ): List<GrokMessage> {
        val messages = mutableListOf<GrokMessage>()
        if (!systemPrompt.isNullOrBlank()) messages.add(GrokMessage("system", systemPrompt))
        for (msg in history) {
            messages.add(GrokMessage(if (msg.isFromUser) "user" else "assistant", msg.text))
        }
        messages.add(GrokMessage("user", userMessage))
        return messages
    }

    private fun callApi(
        url: String,
        apiKey: String,
        model: String,
        messages: List<GrokMessage>,
        maxTokens: Int
    ): AiResponse {
        return try {
            val bodyJson = gson.toJson(GrokRequest(model = model, maxTokens = maxTokens, messages = messages))
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val text = response.body?.string()
                    ?.let { gson.fromJson(it, GrokResponse::class.java) }
                    ?.choices?.firstOrNull()?.message?.content
                if (!text.isNullOrBlank()) {
                    Log.d(TAG, "AI response from $url (${text.length} chars)")
                    AiResponse.Success(text)
                } else {
                    AiResponse.Error("Empty response from AI")
                }
            } else {
                val err = response.body?.string() ?: ""
                Log.w(TAG, "API error ${response.code} from $url: $err")
                AiResponse.Error("${response.code}: $err")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling $url", e)
            AiResponse.Error("Failed to connect: ${e.message}")
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
            "ja" -> "Japanese"
            "pt" -> "Portuguese"
            "ar" -> "Arabic"
            "ru" -> "Russian"
            else -> "English"
        }

        val base = """
            You are Mira, the form assistant on a patient check-in kiosk. You help patients understand form fields, give the right format for values, and explain confusing terms.

            Style:
            - Lead with the answer. No greetings, no "I'd be happy to help", no preamble of any kind.
            - 1 to 3 short sentences in plain everyday words, like a friendly front desk person. No jargon, no corporate tone.
            - Never use an em dash. Use periods and commas instead.
            - Always respond in $languageName.

            Answer everyday paperwork questions directly and confidently from your own knowledge: where things sit on insurance cards, Social Security cards, and driver's licenses, what a member ID or group number is, what a subscriber or policyholder means, and so on. Never tell the patient to ask a staff member, the front desk, or a representative.
            Never give medical advice or diagnoses. If asked for medical advice, say you can't advise on that and offer to flag someone at the clinic to follow up. Only help with form-filling questions.
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

