package com.medpull.kiosk.data.remote.ai

import com.google.gson.annotations.SerializedName

/** Request/response models for the Anthropic Claude API (used by ClaudeVisionService). */

data class ClaudeRequest(
    val model: String,
    @SerializedName("max_tokens") val maxTokens: Int,
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
    @SerializedName("stop_reason") val stopReason: String?,
    val usage: ClaudeUsage?
)

data class ClaudeContent(
    val type: String?,
    val text: String?
)

data class ClaudeUsage(
    @SerializedName("input_tokens") val inputTokens: Int?,
    @SerializedName("output_tokens") val outputTokens: Int?
)
