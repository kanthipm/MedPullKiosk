package com.medpull.kiosk.utils

import com.medpull.kiosk.BuildConfig

/**
 * Application-wide constants
 */
object Constants {

    // AWS Configuration
    object AWS {
        const val REGION = BuildConfig.AWS_REGION
        const val USER_POOL_ID = BuildConfig.AWS_USER_POOL_ID
        const val CLIENT_ID = BuildConfig.AWS_CLIENT_ID
        const val IDENTITY_POOL_ID = BuildConfig.AWS_IDENTITY_POOL_ID
        const val API_ENDPOINT = BuildConfig.AWS_API_ENDPOINT
        const val S3_BUCKET = BuildConfig.AWS_S3_BUCKET

        // S3 Folder structure
        const val S3_FORMS_FOLDER = "forms/"
        const val S3_FILLED_FORMS_FOLDER = "filled-forms/"
        const val S3_AUDIT_LOGS_FOLDER = "audit-logs/"
    }

    // Session Management
    object Session {
        const val TIMEOUT_MS = BuildConfig.SESSION_TIMEOUT_MS
        const val WARNING_THRESHOLD_MS = TIMEOUT_MS - 120000L // 2 minutes before timeout
        const val ACTIVITY_CHECK_INTERVAL_MS = 30000L // Check every 30 seconds
    }

    // Supported Languages
    object Languages {
        const val ENGLISH = "en"
        const val SPANISH = "es"
        const val CHINESE = "zh"
        const val FRENCH = "fr"
        const val HINDI = "hi"
        const val ARABIC = "ar"

        val ALL = listOf(ENGLISH, SPANISH, CHINESE, FRENCH, HINDI, ARABIC)

        fun getLanguageName(code: String): String = when (code) {
            ENGLISH -> "English"
            SPANISH -> "Español"
            CHINESE -> "中文"
            FRENCH -> "Français"
            HINDI -> "हिन्दी"
            ARABIC -> "العربية"
            else -> "English"
        }
    }

    // Database
    object Database {
        const val NAME = "medpull_kiosk.db"
        const val VERSION = 1
    }

    // DataStore
    object DataStore {
        const val PREFERENCES_NAME = "medpull_preferences"
        const val KEY_LANGUAGE = "selected_language"
        const val KEY_LAST_SYNC = "last_sync_timestamp"
        const val KEY_USER_ID = "user_id"
    }

    // Encryption
    object Encryption {
        const val KEYSTORE_ALIAS = "medpull_master_key"
        const val ENCRYPTED_PREFS_NAME = "medpull_secure_prefs"
        const val KEY_AUTH_TOKEN = "auth_token"
        const val KEY_ID_TOKEN = "id_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
    }

    // PDF Processing
    object Pdf {
        const val MAX_FILE_SIZE_MB = 50
        const val SUPPORTED_FORMATS = "application/pdf"
        const val FORM_FIELD_CONFIDENCE_THRESHOLD = 0.80f
    }

    // Network
    object Network {
        const val CONNECT_TIMEOUT_SECONDS = 30L
        const val READ_TIMEOUT_SECONDS = 60L
        const val WRITE_TIMEOUT_SECONDS = 60L
        const val MAX_RETRY_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 1000L
    }

    // AI Assistance — uses Anthropic Claude API directly
    object AI {
        const val CLAUDE_MODEL = "claude-3-haiku-20240307"
        const val CLAUDE_API_URL = "https://api.anthropic.com/v1/messages"
        const val CLAUDE_API_VERSION = "2023-06-01"
        const val MAX_TOKENS = 1024

        // Claude Vision post-processing
        const val CLAUDE_VISION_MODEL = "claude-haiku-4-5-20251001"
        const val VISION_MAX_TOKENS = 8192
        const val VISION_TIMEOUT_SECONDS = 120L
        const val VISION_ENABLED = true
    }

    // Audit Logging
    object Audit {
        const val ACTION_LOGIN = "LOGIN"
        const val ACTION_LOGOUT = "LOGOUT"
        const val ACTION_FORM_UPLOAD = "FORM_UPLOAD"
        const val ACTION_FORM_VIEW = "FORM_VIEW"
        const val ACTION_FORM_EDIT = "FORM_EDIT"
        const val ACTION_FORM_EXPORT = "FORM_EXPORT"
        const val ACTION_AI_QUERY = "AI_QUERY"
        const val ACTION_SESSION_TIMEOUT = "SESSION_TIMEOUT"
    }

    // UI
    object UI {
        const val SPLASH_DELAY_MS = 2000L
        const val TOAST_DURATION_MS = 3000L
        const val ANIMATION_DURATION_MS = 300L
    }

    // Validation
    object Validation {
        const val MIN_PASSWORD_LENGTH = 8
        const val PASSWORD_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@\$!%*?&])[A-Za-z\\d@\$!%*?&]{8,}$"
        const val EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    }
}
