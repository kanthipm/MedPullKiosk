package com.medpull.kiosk.data.remote.ai

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.annotations.SerializedName
import com.medpull.kiosk.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Client for a local Ollama server reached over Tailscale, using the NATIVE
 * `/api/chat` endpoint (not the OpenAI-compatible `/v1`) so we can set
 * `think:false` (disable reasoning for latency) and `format` (JSON-schema
 * structured output) — neither of which propagates through `/v1`.
 *
 * Returns a typed [OllamaResult] so callers can distinguish "no AI at all"
 * (Unreachable) from "model up but slow/bad" (Timeout / HttpError) — the two
 * are handled differently downstream (see plan §3).
 *
 * Residency: every request sends `keep_alive:-1` (belt-and-suspenders) so staying
 * warm doesn't silently depend on the box's OLLAMA_KEEP_ALIVE env var, which is
 * the documented backup mechanism (see README).
 */
class OllamaApiService(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "OllamaApiService"
    }

    /**
     * Send a single-turn structured chat. Returns the model's raw message content.
     *
     * Uses Ollama's simple JSON mode (`format:"json"`). NOTE: full JSON-SCHEMA
     * `format` is NOT honored by the target box (qwen3.5:9b on Ollama 0.23.2) —
     * verified empirically: a strict schema returned plain prose. So the response
     * SHAPE is driven by the explicit key list in the system prompt, and the
     * caller parses defensively. `format:"json"` still guarantees valid JSON here
     * and remains correct on servers that do enforce schemas.
     */
    suspend fun chat(
        systemPrompt: String,
        userPrompt: String
    ): OllamaResult = withContext(Dispatchers.IO) {
        val messages = buildList {
            if (systemPrompt.isNotBlank()) add(OllamaMessage("system", systemPrompt))
            add(OllamaMessage("user", userPrompt))
        }
        post(
            OllamaChatRequest(
                model = Constants.AI.OLLAMA_MODEL,
                messages = messages,
                format = JsonPrimitive("json"),
                options = OllamaOptions(
                    temperature = Constants.AI.COPILOT_TEMPERATURE,
                    numPredict = Constants.AI.COPILOT_NUM_PREDICT
                )
            )
        )
    }

    /**
     * Fire-and-forget preload: an empty `messages` request loads the model into
     * memory (with keep_alive:-1) so the first real question isn't a cold start.
     * Call from the earliest screen; ignore the result.
     */
    suspend fun warmUp(): OllamaResult = withContext(Dispatchers.IO) {
        post(OllamaChatRequest(model = Constants.AI.OLLAMA_MODEL, messages = emptyList()))
    }

    private fun post(body: OllamaChatRequest): OllamaResult {
        val url = Constants.AI.OLLAMA_BASE_URL.trimEnd('/') + "/api/chat"
        return try {
            val request = Request.Builder()
                .url(url)
                .post(gson.toJson(body).toRequestBody("application/json".toMediaType()))
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val content = response.body?.string()
                        ?.let { gson.fromJson(it, OllamaChatResponse::class.java) }
                        ?.message?.content
                        ?: ""   // empty is valid (e.g. warm-up load response)
                    OllamaResult.Success(content)
                } else {
                    val err = response.body?.string() ?: ""
                    Log.w(TAG, "Ollama HTTP ${response.code}: $err")
                    OllamaResult.HttpError(response.code, err)
                }
            }
        } catch (e: SocketTimeoutException) {
            // A *connect* timeout means the host is unreachable; a *read* timeout
            // means the model is up but slow. OkHttp surfaces both as
            // SocketTimeoutException, distinguishable only by message.
            val msg = e.message ?: "timeout"
            if (msg.contains("connect", ignoreCase = true)) OllamaResult.Unreachable(msg)
            else OllamaResult.Timeout(msg)
        } catch (e: InterruptedIOException) {
            OllamaResult.Timeout(e.message ?: "timeout")
        } catch (e: ConnectException) {
            OllamaResult.Unreachable(e.message ?: "connect failed")
        } catch (e: UnknownHostException) {
            OllamaResult.Unreachable(e.message ?: "unknown host")
        } catch (e: Exception) {
            Log.w(TAG, "Ollama call failed: ${e.message}")
            OllamaResult.Unreachable(e.message ?: "error")
        }
    }
}

/** Outcome of a raw Ollama call. */
sealed class OllamaResult {
    data class Success(val content: String) : OllamaResult()
    data class Unreachable(val message: String) : OllamaResult()
    data class Timeout(val message: String) : OllamaResult()
    data class HttpError(val code: Int, val message: String) : OllamaResult()
}

// --- Ollama native /api/chat request/response models ---

data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean = false,
    val think: Boolean = false,
    @SerializedName("keep_alive") val keepAlive: Int = -1,
    val format: JsonElement? = null,
    val options: OllamaOptions? = null
)

data class OllamaMessage(
    val role: String,
    val content: String
)

data class OllamaOptions(
    val temperature: Double,
    @SerializedName("num_predict") val numPredict: Int
)

data class OllamaChatResponse(
    val message: OllamaMessage?,
    val done: Boolean?
)
