package com.medpull.kiosk.data.remote.ai

sealed class AiResponse {
    data class Success(val message: String) : AiResponse()
    data class Error(val message: String) : AiResponse()
}
