package com.medpull.kiosk.data.models

import com.medpull.kiosk.utils.Constants

/**
 * Supported language enum
 */
enum class Language(val code: String, val displayName: String, val nativeName: String) {
    ENGLISH(Constants.Languages.ENGLISH, "English", "English"),
    SPANISH(Constants.Languages.SPANISH, "Spanish", "Español"),
    CHINESE(Constants.Languages.CHINESE, "Chinese", "中文"),
    FRENCH(Constants.Languages.FRENCH, "French", "Français"),
    JAPANESE(Constants.Languages.JAPANESE, "Japanese", "日本語"),
    PORTUGUESE(Constants.Languages.PORTUGUESE, "Portuguese", "Português"),
    ARABIC(Constants.Languages.ARABIC, "Arabic", "العربية"),
    RUSSIAN(Constants.Languages.RUSSIAN, "Russian", "Русский");

    companion object {
        fun fromCode(code: String): Language {
            return values().find { it.code == code } ?: ENGLISH
        }

        fun getAllLanguages(): List<Language> {
            return values().toList()
        }
    }
}
