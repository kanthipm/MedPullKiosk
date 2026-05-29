package com.medpull.kiosk.utils

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages application locale and language switching
 * Persists language selection across app sessions
 */
@Singleton
class LocaleManager @Inject constructor() {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name = Constants.DataStore.PREFERENCES_NAME
    )

    private var applicationContext: Context? = null

    companion object {
        private val LANGUAGE_KEY = stringPreferencesKey(Constants.DataStore.KEY_LANGUAGE)
    }

    /**
     * Initialize with application context
     */
    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    /**
     * Get current language as Flow.
     *
     * Mirrors the same stale-code migration safety as [getCurrentLanguage].
     */
    fun getLanguageFlow(context: Context): Flow<String> {
        return context.dataStore.data.map { preferences ->
            val saved = preferences[LANGUAGE_KEY] ?: Constants.Languages.ENGLISH
            if (saved in Constants.Languages.ALL) saved else Constants.Languages.ENGLISH
        }
    }

    /**
     * Get current language synchronously (use sparingly).
     *
     * Migration safety: if the persisted code isn't in the current supported set
     * (e.g. a stale "hi" left over from a prior install), fall back to English so
     * downstream code never sees an unsupported value.
     */
    fun getCurrentLanguage(context: Context): String = runBlocking {
        context.dataStore.data.map { preferences ->
            val saved = preferences[LANGUAGE_KEY] ?: Constants.Languages.ENGLISH
            if (saved in Constants.Languages.ALL) saved else Constants.Languages.ENGLISH
        }.first()
    }

    /**
     * Set language and update configuration
     */
    suspend fun setLanguage(context: Context, languageCode: String) {
        // Validate language code
        if (!Constants.Languages.ALL.contains(languageCode)) {
            throw IllegalArgumentException("Unsupported language: $languageCode")
        }

        // Save to DataStore
        context.dataStore.edit { preferences ->
            preferences[LANGUAGE_KEY] = languageCode
        }
    }

    /**
     * Apply locale to context (returns updated context)
     */
    fun applyLocale(context: Context, languageCode: String): Context {
        val locale = getLocaleFromCode(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))

        return context.createConfigurationContext(config)
    }

    /**
     * Get Locale object from language code
     */
    private fun getLocaleFromCode(languageCode: String): Locale {
        return when (languageCode) {
            Constants.Languages.ENGLISH -> Locale.ENGLISH
            Constants.Languages.SPANISH -> Locale("es")
            Constants.Languages.CHINESE -> Locale.SIMPLIFIED_CHINESE
            Constants.Languages.FRENCH -> Locale.FRENCH
            Constants.Languages.JAPANESE -> Locale.JAPANESE
            Constants.Languages.PORTUGUESE -> Locale("pt")
            Constants.Languages.ARABIC -> Locale("ar")
            Constants.Languages.RUSSIAN -> Locale("ru")
            else -> Locale.ENGLISH
        }
    }

    /**
     * Get display name for language
     */
    fun getLanguageDisplayName(languageCode: String): String {
        return Constants.Languages.getLanguageName(languageCode)
    }

    /**
     * Get all supported languages.
     *
     * Both [LanguageOption.displayName] and [LanguageOption.nativeName] return the
     * language's name written in its own script (e.g. "中文", "العربية", "Русский").
     * The 2x4 picker grid relies on this so each card renders in its own writing
     * system regardless of the currently active locale.
     */
    fun getSupportedLanguages(): List<LanguageOption> {
        return Constants.Languages.ALL.map { code ->
            val nativeName = getLanguageDisplayName(code)
            LanguageOption(
                code = code,
                displayName = nativeName,
                nativeName = nativeName
            )
        }
    }

    /**
     * Check if RTL (Right-to-Left) language
     */
    fun isRtl(languageCode: String): Boolean {
        return languageCode == Constants.Languages.ARABIC
    }
}

/**
 * Data class for language selection
 */
data class LanguageOption(
    val code: String,
    val displayName: String,
    val nativeName: String
)
