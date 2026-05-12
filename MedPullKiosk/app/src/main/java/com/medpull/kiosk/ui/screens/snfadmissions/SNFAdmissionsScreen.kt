package com.medpull.kiosk.ui.screens.snfadmissions

import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.medpull.kiosk.BuildConfig
import com.medpull.kiosk.utils.Constants
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "SNFAdmissionsScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SNFAdmissionsScreen(onBack: () -> Unit) {
    var webView: WebView? by remember { mutableStateOf(null) }

    BackHandler {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SNF Admissions Copilot") },
                navigationIcon = {
                    IconButton(onClick = {
                        val wv = webView
                        if (wv != null && wv.canGoBack()) wv.goBack() else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AndroidView(
                factory = { context ->
                    val assetLoader = WebViewAssetLoader.Builder()
                        .setDomain("appassets.androidplatform.net")
                        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                        .build()

                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true

                        // Bridge: JS calls window.AndroidBridge.analyzeDocuments(json)
                        // and window.AndroidBridge.getApiConfig() for model info
                        addJavascriptInterface(SNFBridge(), "AndroidBridge")

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest
                            ): WebResourceResponse? =
                                assetLoader.shouldInterceptRequest(request.url)

                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest
                            ): Boolean = false
                        }

                        loadUrl("https://appassets.androidplatform.net/assets/snf-admissions/index.html")
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * JavaScript bridge — the web app calls these methods to use the app's
 * existing Grok (xAI) API key without ever seeing it.
 *
 * Called on a background thread by the WebView, so OkHttp blocking calls are fine.
 */
private class SNFBridge {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Analyze extracted document text with Grok.
     *
     * @param documentsJson JSON array: [{ "name": "...", "text": "..." }, ...]
     * @return JSON string: PatientProfile on success, { "error": "..." } on failure
     */
    @JavascriptInterface
    fun analyzeDocuments(documentsJson: String): String {
        val apiKey = BuildConfig.GROK_API_KEY.ifBlank { BuildConfig.GROQ_API_KEY }
        if (apiKey.isBlank()) {
            Log.w(TAG, "No API key configured — returning demo signal")
            return """{"demo":true}"""
        }

        return try {
            val docsArray = JSONArray(documentsJson)
            val combined = buildString {
                for (i in 0 until docsArray.length()) {
                    val doc = docsArray.getJSONObject(i)
                    append("=== FILE: ${doc.getString("name")} ===\n")
                    append(doc.getString("text").take(12000))
                    append("\n\n")
                }
            }

            val isGrok = BuildConfig.GROK_API_KEY.isNotBlank()
            val apiUrl = if (isGrok) "${Constants.AI.GROK_API_URL.removeSuffix("/chat/completions")}/chat/completions"
                         else Constants.AI.GROQ_API_URL
            val model  = if (isGrok) Constants.AI.GROK_MODEL else Constants.AI.GROQ_MODEL

            val requestBody = JSONObject().apply {
                put("model", model)
                put("temperature", 0)
                put("max_tokens", 4096)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", SYSTEM_PROMPT)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "Analyze these hospital transfer documents:\n\n$combined")
                    })
                })
            }

            val request = Request.Builder()
                .url(apiUrl)
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return """{"error":"Empty response from AI"}"""

            if (!response.isSuccessful) {
                Log.e(TAG, "API error ${response.code}: $body")
                return """{"error":"AI API error ${response.code}"}"""
            }

            // Extract the content from choices[0].message.content
            val choices = JSONObject(body).getJSONArray("choices")
            val content = choices.getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

            // Validate it parses as JSON, then return as-is
            JSONObject(content) // throws if invalid
            content

        } catch (e: Exception) {
            Log.e(TAG, "analyzeDocuments failed", e)
            """{"error":"${e.message?.replace("\"", "'")}"}"""
        }
    }

    /**
     * Returns which model is configured so the web app can show it in the UI.
     */
    @JavascriptInterface
    fun getApiConfig(): String {
        val hasGrok = BuildConfig.GROK_API_KEY.isNotBlank()
        val hasGroq = BuildConfig.GROQ_API_KEY.isNotBlank()
        return JSONObject().apply {
            put("hasKey", hasGrok || hasGroq)
            put("model", if (hasGrok) Constants.AI.GROK_MODEL else if (hasGroq) Constants.AI.GROQ_MODEL else "demo")
            put("provider", if (hasGrok) "Grok (xAI)" else if (hasGroq) "Groq" else "demo")
        }.toString()
    }

    companion object {
        private const val SYSTEM_PROMPT = """You are an expert SNF admissions coordinator AI assistant. Analyze hospital transfer documents and extract structured patient information including a full medication reconciliation review.

Return ONLY a valid JSON object — no markdown, no prose outside JSON.

Schema:
{
  "snapshot": { "name": string|null, "dob": "YYYY-MM-DD"|null, "age": number|null, "mrn": string|null, "admittingDiagnosis": string|null, "diagnoses": string[], "dischargeDestination": string|null, "adlStatus": string|null, "attendingProvider": string|null },
  "clinical": { "summary": string|null, "mobilityStatus": string|null, "rehabNeeds": string|null, "psychiatricRisks": string|null, "fallRisk": "low"|"moderate"|"high"|"unknown", "medicationAdherenceConcerns": string|null, "precautions": string[] },
  "medications": [{ "id": string, "name": string, "dosage": string, "frequency": string, "route": string, "indication": string|null, "alerts": string[], "source": string|null }],
  "insurance": { "payerSource": string|null, "memberId": string|null, "groupNumber": string|null, "authorizationStatus": "authorized"|"pending"|"denied"|"unknown", "authorizationNumber": string|null, "coveredDays": number|null, "missingInfo": string[], "reimbursementConcerns": string|null },
  "issues": [{ "id": string, "title": string, "description": string, "severity": "warning"|"error", "field": string|null }],
  "risks": { "fallRisk": "low"|"moderate"|"high"|"unknown", "behavioralRisk": "low"|"moderate"|"high"|"unknown", "medicationNoncompliance": "low"|"moderate"|"high"|"unknown", "housingInstability": "low"|"moderate"|"high"|"unknown", "readmissionRisk": "low"|"moderate"|"high"|"unknown" },
  "timeline": [{ "date": "YYYY-MM-DD", "event": string, "facility": string|null }],
  "reconciliation": {
    "summary": { "totalReviewed": number, "discrepanciesDetected": number, "highRiskMedications": number, "unresolvedAllergyConflicts": number },
    "medications": [{ "id": string, "name": string, "dose": string, "frequency": string, "sources": string[], "status": "verified"|"conflict"|"missing_from_mar"|"discontinued"|"needs_review", "riskLevel": "high"|"moderate"|"standard", "confidence": number, "highRiskCategory": string|null, "highRiskReason": string|null, "sourceSnippets": [{ "document": string, "text": string }] }],
    "alerts": [{ "id": string, "severity": "critical"|"warning"|"info", "message": string, "medications": string[] }],
    "recommendedActions": [{ "id": string, "action": string, "priority": "urgent"|"routine" }]
  }
}

Rules:
- Extract ALL medications from every document. Cross-reference discharge summary vs MAR vs fax notes.
- Flag conflicts: medication active in MAR but held/discontinued in discharge summary, or vice versa.
- Flag missing_from_mar: medication in discharge summary not found in MAR.
- Flag high-risk categories: anticoagulants, insulin, opioids, sedatives, antipsychotics, fall-risk medications.
- Detect allergy conflicts across documents and include as a critical alert.
- For each reconciliation medication include sourceSnippets with the exact text found in each document.
- Generate specific recommended actions for each discrepancy.
- issues.id = "i1","i2"... medications[].id = "m1","m2"... reconciliation.medications[].id = "r1","r2"... alerts[].id = "ra1","ra2"... recommendedActions[].id = "ac1","ac2"..."""
    }
}
