package com.medpull.kiosk.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.medpull.kiosk.utils.SpeechPrewarmerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wakes the shared TTS + speech-recognition services while a screen that PRECEDES
 * guided intake is on display, so the first spoken question and first mic open are
 * instant instead of stalling on cold-start. Drop this into program/form selection.
 * No-ops past the first warm for a given language (the prewarmer is app-scoped).
 */
@Composable
fun PrewarmSpeech() {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        runCatching {
            val ep = EntryPointAccessors.fromApplication(
                context.applicationContext,
                SpeechPrewarmerEntryPoint::class.java
            )
            // getCurrentLanguage reads DataStore via runBlocking — keep it off the
            // main thread; warm() itself re-posts to main for the engine calls.
            val language = withContext(Dispatchers.IO) {
                ep.localeManager().getCurrentLanguage(context)
            }
            ep.speechPrewarmer().warm(language)
        }
    }
}
