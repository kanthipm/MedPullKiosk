package com.medpull.kiosk.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.medpull.kiosk.utils.SessionManager

/**
 * Tracks screen activity and records it with SessionManager
 * Should be called at the top of each authenticated screen
 */
@Composable
fun ActivityTracker(sessionManager: SessionManager) {
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // Record activity when screen becomes visible
                    sessionManager.recordActivity()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    // Screen paused, activity tracking stops
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
