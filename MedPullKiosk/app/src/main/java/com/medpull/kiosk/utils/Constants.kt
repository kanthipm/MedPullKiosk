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
        const val JAPANESE = "ja"
        const val PORTUGUESE = "pt"
        const val ARABIC = "ar"
        const val RUSSIAN = "ru"

        // Display order on the 2x4 picker grid (left-to-right, top-to-bottom):
        // English  | Español
        // 中文     | Français
        // 日本語   | Português
        // العربية | Русский
        val ALL = listOf(ENGLISH, SPANISH, CHINESE, FRENCH, JAPANESE, PORTUGUESE, ARABIC, RUSSIAN)

        fun getLanguageName(code: String): String = when (code) {
            ENGLISH -> "English"
            SPANISH -> "Español"
            CHINESE -> "中文"
            FRENCH -> "Français"
            JAPANESE -> "日本語"
            PORTUGUESE -> "Português"
            ARABIC -> "العربية"
            RUSSIAN -> "Русский"
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

    // AI Assistance — uses Grok (xAI) API for intake conversation
    object AI {
        // Grok API (OpenAI-compatible)
        const val GROK_API_URL = "https://api.x.ai/v1/chat/completions"
        const val GROK_MODEL = "grok-3"
        const val MAX_TOKENS = 1024

        // Conversational intake engine — Grok (BAA-covered, handles all PHI)
        const val CONVERSATION_MODEL = "grok-3"
        const val CONVERSATION_MAX_TOKENS = 1024

        // Mira chat assistant — grok-3-mini (same BAA, cheaper for simple Q&A)
        const val CHAT_ASSISTANT_MODEL = "grok-3-mini"
        const val CHAT_MAX_TOKENS = 512

        // Groq (groq.com) — free-tier fallback, OpenAI-compatible, used when Grok is unavailable
        const val GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions"
        const val GROQ_MODEL = "llama-3.3-70b-versatile"

        // Vision: disabled — no BAA with Anthropic, Textract handles OCR
        const val ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages"
        const val ANTHROPIC_API_VERSION = "2023-06-01"
        const val VISION_MAX_TOKENS = 8192
        const val VISION_TIMEOUT_SECONDS = 120L
        const val VISION_ENABLED = false

        // ─── Local AI Co-Pilot (Ollama over Tailscale) ──────────────────────
        // Native /api/chat on a local Ollama box; the primary brain of the
        // intake path (replaces Grok there — see memory/feedback_avoid_grok.md).
        //
        // CALIBRATION WARNING: every threshold/timeout below is an UNCALIBRATED,
        // model-specific STARTING value. A model's self-reported `confidence` is
        // NOT a calibrated probability and is not comparable across models, so the
        // 0.75 carried over from the Grok era means nothing for qwen3.5. Tune these
        // from the on-device copilot_audit_logs (confidence vs. actual correctness,
        // and observed p95/p99 latency) — do not trust the numbers on faith.
        const val OLLAMA_BASE_URL = BuildConfig.OLLAMA_BASE_URL  // tailnet host:port, from local.properties
        const val OLLAMA_MODEL = "qwen3.5:9b"                    // verify against `ollama list` on the box
        const val COPILOT_ENABLED = true
        const val COPILOT_CONFIDENCE_THRESHOLD = 0.75f           // accept gate (uncalibrated)
        const val COPILOT_BRANCH_THRESHOLD = 0.85f               // auto-branch gate — deliberately higher; a wrong jump is worse than a wrong clarify
        const val COPILOT_ALSOFILL_ENABLED = false               // inferred PHI into unconfirmed fields — OFF for the testing phase
        const val COPILOT_ALSOFILL_THRESHOLD = 0.9f              // when enabled, only apply also_fills at/above this
        const val COPILOT_TIMEOUT_SECONDS = 8L                   // headroom over ~5.5s worst case; re-tune from logged p95/p99
        const val COPILOT_NUM_PREDICT = 300
        const val COPILOT_TEMPERATURE = 0.1
        const val COPILOT_AUDIO_CUE_ENABLED = true               // brief tone before a spoken assistant intervention

        // ─── Web-search fallback (co-pilot needs_search) ────────────────────
        // Provider-agnostic: the client interface is swappable; the default is
        // DuckDuckGo's HTML endpoint via one plain HTTP GET (headless-safe, no
        // browser). Endpoint comes from local.properties → BuildConfig.
        const val SEARCH_ENABLED = true
        const val SEARCH_BASE_URL = BuildConfig.SEARCH_BASE_URL
        const val SEARCH_CONNECT_TIMEOUT_SECONDS = 4L
        const val SEARCH_READ_TIMEOUT_SECONDS = 8L
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

    // Google Sheets Inventory
    object GoogleSheets {
        const val BASE_URL = "https://sheets.googleapis.com/v4/"
        const val API_KEY = BuildConfig.GOOGLE_SHEETS_API_KEY
        const val SPREADSHEET_ID = BuildConfig.INVENTORY_SPREADSHEET_ID
    }

    // Validation
    object Validation {
        const val MIN_PASSWORD_LENGTH = 8
        const val PASSWORD_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@\$!%*?&])[A-Za-z\\d@\$!%*?&]{8,}$"
        const val EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    }
}
