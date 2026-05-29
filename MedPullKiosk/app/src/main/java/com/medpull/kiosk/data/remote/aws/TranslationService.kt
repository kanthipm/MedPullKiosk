package com.medpull.kiosk.data.remote.aws

import com.amazonaws.services.translate.AmazonTranslateClient
import com.amazonaws.services.translate.model.TranslateTextRequest
import com.medpull.kiosk.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AWS Translate service for text translation
 * Translates form fields between languages
 */
@Singleton
class TranslationService @Inject constructor(
    private val translateClient: AmazonTranslateClient
) {

    /**
     * Translate text from English to target language
     */
    suspend fun translateToLanguage(
        text: String,
        targetLanguageCode: String
    ): TranslationResult = withContext(Dispatchers.IO) {
        try {
            if (targetLanguageCode == Constants.Languages.ENGLISH) {
                // No translation needed
                return@withContext TranslationResult.Success(text)
            }

            val awsLanguageCode = mapLanguageCode(targetLanguageCode)

            val request = TranslateTextRequest()
                .withText(text)
                .withSourceLanguageCode("en")
                .withTargetLanguageCode(awsLanguageCode)

            val result = translateClient.translateText(request)

            TranslationResult.Success(result.translatedText)
        } catch (e: Exception) {
            TranslationResult.Error(e.message ?: "Translation failed")
        }
    }

    /**
     * Translate text from target language back to English
     */
    suspend fun translateToEnglish(
        text: String,
        sourceLanguageCode: String
    ): TranslationResult = withContext(Dispatchers.IO) {
        try {
            if (sourceLanguageCode == Constants.Languages.ENGLISH) {
                // No translation needed
                return@withContext TranslationResult.Success(text)
            }

            val awsLanguageCode = mapLanguageCode(sourceLanguageCode)

            val request = TranslateTextRequest()
                .withText(text)
                .withSourceLanguageCode(awsLanguageCode)
                .withTargetLanguageCode("en")

            val result = translateClient.translateText(request)

            TranslationResult.Success(result.translatedText)
        } catch (e: Exception) {
            TranslationResult.Error(e.message ?: "Translation failed")
        }
    }

    /**
     * Translate text to English with automatic source language detection.
     * AWS Translate supports "auto" as source language code.
     */
    suspend fun translateToEnglishAutoDetect(
        text: String
    ): TranslationResult = withContext(Dispatchers.IO) {
        try {
            if (text.isBlank()) {
                return@withContext TranslationResult.Success(text)
            }

            val request = TranslateTextRequest()
                .withText(text)
                .withSourceLanguageCode("auto")
                .withTargetLanguageCode("en")

            val result = translateClient.translateText(request)

            TranslationResult.Success(result.translatedText)
        } catch (e: Exception) {
            TranslationResult.Error(e.message ?: "Translation failed")
        }
    }

    /**
     * Translate field values to English with automatic source language detection.
     * Throws if ALL translations fail (indicates a systemic issue like missing IAM permissions).
     */
    suspend fun translateFieldValuesToEnglishAutoDetect(
        values: Map<String, String>
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val translatedValues = mutableMapOf<String, String>()
        var successCount = 0
        var firstError: String? = null

        values.forEach { (fieldId, userInput) ->
            when (val result = translateToEnglishAutoDetect(userInput)) {
                is TranslationResult.Success -> {
                    translatedValues[fieldId] = result.translatedText
                    successCount++
                    if (result.translatedText != userInput) {
                        android.util.Log.d("TranslationService", "Translated: '$userInput' -> '${result.translatedText}'")
                    } else {
                        android.util.Log.d("TranslationService", "Already English (no change): '$userInput'")
                    }
                }
                is TranslationResult.Error -> {
                    translatedValues[fieldId] = userInput
                    if (firstError == null) firstError = result.message
                    android.util.Log.e("TranslationService", "Translation FAILED for '$userInput': ${result.message}")
                }
            }
        }

        // If every single translation failed, throw so the caller can surface the error
        if (successCount == 0 && firstError != null) {
            throw RuntimeException("Translation failed: $firstError")
        }

        translatedValues
    }

    /**
     * Translate batch of texts
     */
    suspend fun translateBatch(
        texts: List<String>,
        targetLanguageCode: String
    ): List<TranslationResult> = withContext(Dispatchers.IO) {
        texts.map { text ->
            translateToLanguage(text, targetLanguageCode)
        }
    }

    /**
     * Translate form fields to target language
     */
    suspend fun translateFormFields(
        fields: Map<String, String>, // fieldName to originalText
        targetLanguageCode: String
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val translatedFields = mutableMapOf<String, String>()

        fields.forEach { (fieldName, originalText) ->
            when (val result = translateToLanguage(originalText, targetLanguageCode)) {
                is TranslationResult.Success -> {
                    translatedFields[fieldName] = result.translatedText
                }
                is TranslationResult.Error -> {
                    // Keep original text if translation fails
                    translatedFields[fieldName] = originalText
                }
            }
        }

        translatedFields
    }

    /**
     * Translate form field values back to English for export
     */
    suspend fun translateFieldValuesToEnglish(
        values: Map<String, String>, // fieldId to user input
        sourceLanguageCode: String
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val translatedValues = mutableMapOf<String, String>()

        values.forEach { (fieldId, userInput) ->
            when (val result = translateToEnglish(userInput, sourceLanguageCode)) {
                is TranslationResult.Success -> {
                    translatedValues[fieldId] = result.translatedText
                }
                is TranslationResult.Error -> {
                    // Keep original value if translation fails
                    translatedValues[fieldId] = userInput
                }
            }
        }

        translatedValues
    }

    /**
     * Map app language codes to AWS Translate language codes
     */
    private fun mapLanguageCode(appLanguageCode: String): String {
        return when (appLanguageCode) {
            Constants.Languages.ENGLISH -> "en"
            Constants.Languages.SPANISH -> "es"
            Constants.Languages.CHINESE -> "zh" // AWS uses 'zh' for simplified Chinese
            Constants.Languages.FRENCH -> "fr"
            Constants.Languages.JAPANESE -> "ja"
            Constants.Languages.PORTUGUESE -> "pt"
            Constants.Languages.ARABIC -> "ar"
            Constants.Languages.RUSSIAN -> "ru"
            else -> "en" // Default to English
        }
    }

    /**
     * Check if translation is supported for language pair
     */
    fun isTranslationSupported(sourceCode: String, targetCode: String): Boolean {
        val supportedCodes = listOf("en", "es", "zh", "fr", "ja", "pt", "ar", "ru")
        val awsSource = mapLanguageCode(sourceCode)
        val awsTarget = mapLanguageCode(targetCode)

        return awsSource in supportedCodes && awsTarget in supportedCodes
    }
}

/**
 * Translation result sealed class
 */
sealed class TranslationResult {
    data class Success(val translatedText: String) : TranslationResult()
    data class Error(val message: String) : TranslationResult()
}
