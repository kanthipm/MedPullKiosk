package com.medpull.kiosk.data.remote.ai

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
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
 * OpenAI API service for AI assistance
 */
@Singleton
class OpenAiService @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "OpenAiService"
        private const val API_URL = "https://api.openai.com/v1/chat/completions"
        private const val MODEL = "gpt-3.5-turbo"
    }

    /**
     * Send a chat message to OpenAI
     */
    suspend fun sendMessage(
        message: String,
        context: String? = null,
        language: String = "en"
    ): AiResponse = withContext(Dispatchers.IO) {
        try {
            // Build system prompt with context
            val systemPrompt = buildSystemPrompt(language, context)

            // Create request body
            val chatRequest = ChatCompletionRequest(
                model = MODEL,
                messages = listOf(
                    ChatMessage(role = "system", content = systemPrompt),
                    ChatMessage(role = "user", content = message)
                ),
                temperature = 0.7,
                maxTokens = 500
            )

            val requestBody = gson.toJson(chatRequest)
                .toRequestBody("application/json".toMediaType())

            // Build HTTP request
            val request = Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer ")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            // Execute request
            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val chatResponse = gson.fromJson(responseBody, ChatCompletionResponse::class.java)
                    val aiMessage = chatResponse.choices.firstOrNull()?.message?.content
                        ?: "Sorry, I couldn't generate a response."

                    Log.d(TAG, "AI response: $aiMessage")
                    AiResponse.Success(aiMessage)
                } else {
                    Log.e(TAG, "Empty response body")
                    AiResponse.Error("Empty response from AI")
                }
            } else {
                val errorBody = response.body?.string()
                Log.e(TAG, "API error: ${response.code} - $errorBody")

                // Handle specific error cases
                when (response.code) {
                    401 -> AiResponse.Error("Invalid API key")
                    429 -> AiResponse.Error("Rate limit exceeded. Please try again later.")
                    500 -> AiResponse.Error("AI service temporarily unavailable")
                    else -> AiResponse.Error("AI request failed: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message to AI", e)
            AiResponse.Error("Failed to connect to AI: ${e.message}")
        }
    }

    /**
     * Build system prompt based on language and context
     */
    private fun buildSystemPrompt(language: String, context: String?): String {
        val languageName = when (language) {
            "es" -> "Spanish"
            "zh" -> "Chinese"
            "fr" -> "French"
            "hi" -> "Hindi"
            "ar" -> "Arabic"
            else -> "English"
        }

        val basePrompt = """
            You are a helpful medical form assistant. Your role is to:
            1. Help users understand medical form fields
            2. Suggest appropriate values for form fields
            3. Explain medical terminology in simple terms
            4. Answer questions about the form
            5. Provide guidance in $languageName

            Keep responses concise (2-3 sentences) and helpful.
            Always respond in $languageName.
        """.trimIndent()

        return if (context != null) {
            "$basePrompt\n\nCurrent form context: $context"
        } else {
            basePrompt
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

        return sendMessage(prompt, null, language)
    }

    /**
     * Explain medical term
     */
    suspend fun explainMedicalTerm(
        term: String,
        language: String = "en"
    ): AiResponse {
        val prompt = "What does the medical term '$term' mean? Explain in simple terms."
        return sendMessage(prompt, null, language)
    }
}

/**
 * OpenAI API models
 */
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    @SerializedName("max_tokens")
    val maxTokens: Int = 500
)

data class ChatMessage(
    val role: String, // "system", "user", or "assistant"
    val content: String
)

data class ChatCompletionResponse(
    val id: String,
    val choices: List<Choice>,
    val usage: Usage
)

data class Choice(
    val index: Int,
    val message: ChatMessage,
    @SerializedName("finish_reason")
    val finishReason: String
)

data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int,
    @SerializedName("completion_tokens")
    val completionTokens: Int,
    @SerializedName("total_tokens")
    val totalTokens: Int
)
