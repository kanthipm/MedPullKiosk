package com.medpull.kiosk.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Warms the shared system speech engines BEFORE the guided-intake screen opens.
 *
 * Both the text-to-speech voice model and the speech-recognition service pay a
 * one-time cold-start cost on first use: the voice model loads into the TTS
 * service, and the RecognitionService process binds. When that cost lands on the
 * first spoken question / first mic open, the user sees a 1–2s stall on both.
 * Triggering this from the PRECEDING screen (program / form selection) pays both
 * costs while the user is still reading and tapping, so intake starts instantly.
 *
 * App-scoped so the warmth survives navigation into intake: the intake screen
 * builds its own TextToSpeech instance and per-session recognizers, but those bind
 * to the SAME shared system services this class already woke up. We keep our own
 * instances alive (never speak audibly, never listen) purely to hold those
 * services warm for the rest of the session.
 */
@Singleton
class SpeechPrewarmer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val main = Handler(Looper.getMainLooper())

    private var warmTts: TextToSpeech? = null
    private var parkedRecognizer: SpeechRecognizer? = null
    private var warmedLanguage: String? = null

    /**
     * Warm both engines for [languageCode] (e.g. "es", "zh"). Safe to call
     * repeatedly and from any thread; no-ops if already warm for that language.
     */
    fun warm(languageCode: String) {
        // SpeechRecognizer and TextToSpeech must be created/used on the main thread.
        main.post { warmOnMain(languageCode) }
    }

    private fun warmOnMain(languageCode: String) {
        if (warmedLanguage == languageCode && warmTts != null) return
        warmedLanguage = languageCode
        warmTts(languageCode)
        warmStt(languageCode)
    }

    private fun warmTts(languageCode: String) {
        val locale = ttsLocale(languageCode)
        // Reuse the engine across language changes — just reset its locale and
        // re-prime. Rebuilding would re-pay the service bind cost.
        val existing = warmTts
        if (existing != null) {
            try { existing.language = locale } catch (_: Exception) {}
            primeTts(existing)
            return
        }
        try {
            var engine: TextToSpeech? = null
            engine = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val e = engine ?: return@TextToSpeech
                    try { e.language = locale } catch (_: Exception) {}
                    primeTts(e)
                } else {
                    Log.w(TAG, "Prewarm TTS init failed (status=$status)")
                }
            }
            warmTts = engine
        } catch (e: Exception) {
            Log.w(TAG, "Prewarm TTS create failed", e)
        }
    }

    /**
     * A REAL synthesis (not playSilentUtterance) is what forces the voice model to
     * load — a silent utterance skips the model entirely on many engines, so the
     * first real question still pays the load cost. We drive volume to zero so
     * nothing is audible while still exercising the full synthesis pipeline.
     */
    private fun primeTts(engine: TextToSpeech) {
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0f)
        }
        try {
            engine.speak(WARM_TEXT, TextToSpeech.QUEUE_FLUSH, params, "prewarm")
        } catch (e: Exception) {
            Log.w(TAG, "Prewarm TTS speak failed", e)
        }
    }

    private fun warmStt(languageCode: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        // Park ONE recognizer alive so the service process stays bound. Querying
        // support (never startListening) binds the service without opening the mic
        // or playing a chime. The old in-screen warm destroyed its recognizer in
        // the callback, letting the service unbind again before the first real
        // listen — re-paying the bind cost. Holding the instance avoids that.
        try { parkedRecognizer?.destroy() } catch (_: Exception) {}
        try {
            val rec = SpeechRecognizer.createSpeechRecognizer(context)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, sttLocaleTag(languageCode))
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }
            rec.checkRecognitionSupport(
                intent,
                context.mainExecutor,
                object : RecognitionSupportCallback {
                    override fun onSupportResult(recognitionSupport: RecognitionSupport) {}
                    override fun onError(error: Int) {}
                }
            )
            parkedRecognizer = rec
        } catch (e: Exception) {
            Log.w(TAG, "Prewarm STT failed", e)
        }
    }

    private fun ttsLocale(code: String): Locale = when (code) {
        "es" -> Locale("es", "ES"); "zh" -> Locale.SIMPLIFIED_CHINESE
        "fr" -> Locale("fr", "FR"); "ja" -> Locale.JAPANESE
        "pt" -> Locale("pt", "BR"); "ar" -> Locale("ar", "SA")
        "ru" -> Locale("ru", "RU")
        else -> Locale.US
    }

    private fun sttLocaleTag(code: String): String = when (code) {
        "es" -> "es-ES"; "zh" -> "zh-CN"; "fr" -> "fr-FR"; "ja" -> "ja-JP"
        "pt" -> "pt-BR"; "ar" -> "ar-SA"; "ru" -> "ru-RU"
        else -> "en-US"
    }

    companion object {
        private const val TAG = "SpeechPrewarmer"
        // Short real token: content is irrelevant (it's inaudible at volume 0); we
        // only need the engine to run an actual synthesis so the voice model loads.
        private const val WARM_TEXT = "ok"
    }
}

/**
 * Lets composables without a ViewModel (e.g. ProgramSelectionScreen) reach the
 * app-scoped [SpeechPrewarmer] and [LocaleManager] singletons to trigger warming.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SpeechPrewarmerEntryPoint {
    fun speechPrewarmer(): SpeechPrewarmer
    fun localeManager(): LocaleManager
}
