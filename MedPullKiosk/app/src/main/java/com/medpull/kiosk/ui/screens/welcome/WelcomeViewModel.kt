package com.medpull.kiosk.ui.screens.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medpull.kiosk.data.remote.ai.OllamaApiService
import com.medpull.kiosk.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Welcome screen
 * Kiosk mode: No session restoration, each user starts fresh
 */
@HiltViewModel
class WelcomeViewModel @Inject constructor(
    val sessionManager: SessionManager,
    private val ollama: OllamaApiService
) : ViewModel() {

    /**
     * Start new session when user enters app.
     *
     * Also fires a fire-and-forget Ollama warm-up: this is the earliest screen, so
     * preloading the model here means it's resident by the first real question
     * (no cold start). Failures are intentionally ignored — the kiosk degrades to
     * the deterministic flow if the box is unreachable.
     */
    fun startSession() {
        sessionManager.startSession()
        viewModelScope.launch { runCatching { ollama.warmUp() } }
    }
}
