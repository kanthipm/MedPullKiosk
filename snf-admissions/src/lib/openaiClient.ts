import type { PatientProfile } from './types'

// Injected by SNFAdmissionsScreen.kt when running inside the kiosk WebView
declare global {
  interface Window {
    AndroidBridge?: {
      analyzeDocuments: (documentsJson: string) => string
      getApiConfig: () => string
    }
  }
}

/**
 * Returns true when running inside the Android kiosk WebView with the bridge available.
 */
export function hasBridge(): boolean {
  return typeof window.AndroidBridge !== 'undefined'
}

/**
 * Returns info about which AI provider is configured.
 * When running in the kiosk this reads from the existing BuildConfig key;
 * outside the kiosk it checks localStorage for a manually entered key.
 */
export function getApiConfig(): { hasKey: boolean; model: string; provider: string } {
  if (hasBridge()) {
    try {
      return JSON.parse(window.AndroidBridge!.getApiConfig())
    } catch {
      // ignore
    }
  }
  const key = getStoredApiKey()
  const isGrok = key.startsWith('xai-')
  return {
    hasKey: key.length > 0,
    model: isGrok ? 'grok-3' : 'gpt-4o',
    provider: isGrok ? 'Grok (xAI)' : 'OpenAI (dev)',
  }
}

/**
 * Analyze documents using whichever API is available:
 *   1. Android bridge → Grok/Groq via BuildConfig key (production, no key exposure)
 *   2. Direct OpenAI call via localStorage key (browser dev mode only)
 *   3. Demo mode fallback
 */
export async function analyzeDocuments(
  fileTexts: { name: string; text: string }[],
  onProgress?: (step: string) => void,
): Promise<{ profile: Omit<PatientProfile, 'meta'>; demo: boolean }> {

  // ── Path 1: Android bridge (kiosk) ─────────────────────────────────────────
  if (hasBridge()) {
    onProgress?.('Sending to AI model via bridge…')
    const raw = window.AndroidBridge!.analyzeDocuments(JSON.stringify(fileTexts))
    const parsed = JSON.parse(raw)
    if (parsed.demo) return { profile: null as never, demo: true }
    if (parsed.error) throw new Error(parsed.error)
    return { profile: parsed, demo: false }
  }

  // ── Path 2: Direct API call (browser / dev) ─────────────────────────────────
  const apiKey = getStoredApiKey()
  if (apiKey) {
    const isGrok = apiKey.startsWith('xai-')
    onProgress?.(`Sending to ${isGrok ? 'Grok' : 'OpenAI'}…`)
    const OpenAI = (await import('openai')).default
    const client = new OpenAI({
      apiKey,
      baseURL: isGrok ? 'https://api.x.ai/v1' : undefined,
      dangerouslyAllowBrowser: true,
    })
    const model = isGrok ? 'grok-3' : 'gpt-4o'
    const combined = fileTexts.map((f) => `=== FILE: ${f.name} ===\n${f.text}`).join('\n\n')
    const completion = await client.chat.completions.create({
      model,
      messages: [
        { role: 'system', content: SYSTEM_PROMPT },
        { role: 'user', content: `Analyze these hospital transfer documents:\n\n${combined}` },
      ],
      temperature: 0,
      max_tokens: 4096,
    })
    const raw = (completion.choices[0]?.message?.content ?? '')
      .replace(/^```json\s*/i, '').replace(/^```\s*/i, '').replace(/```\s*$/i, '').trim()
    return { profile: JSON.parse(raw), demo: false }
  }

  // ── Path 3: Demo fallback ────────────────────────────────────────────────────
  return { profile: null as never, demo: true }
}

// ─── API key helpers (dev/browser only) ─────────────────────────────────────

export function getStoredApiKey(): string {
  // localStorage override first, then the key injected from local.properties at build time
  return localStorage.getItem('snf_openai_key') || __LOCAL_API_KEY__ || ''
}

export function saveApiKey(key: string) {
  localStorage.setItem('snf_openai_key', key)
}

// ─── System prompt (kept in sync with SNFBridge.kt) ─────────────────────────

const SYSTEM_PROMPT = `You are an expert SNF admissions coordinator AI assistant. Analyze hospital transfer documents and extract structured patient information including a full medication reconciliation review.

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
    "medications": [{
      "id": string,
      "name": string,
      "dose": string,
      "frequency": string,
      "sources": string[],
      "status": "verified"|"conflict"|"missing_from_mar"|"discontinued"|"needs_review",
      "riskLevel": "high"|"moderate"|"standard",
      "confidence": number,
      "highRiskCategory": string|null,
      "highRiskReason": string|null,
      "sourceSnippets": [{ "document": string, "text": string }]
    }],
    "alerts": [{ "id": string, "severity": "critical"|"warning"|"info", "message": string, "medications": string[] }],
    "recommendedActions": [{ "id": string, "action": string, "priority": "urgent"|"routine" }]
  },
  "followUp": {
    "admissionReadiness": "ready"|"ready_with_warnings"|"pending_critical"|"high_risk",
    "admissionReadinessReason": string,
    "actions": [{
      "id": string,
      "issueTitle": string,
      "description": string,
      "owner": "admissions"|"nursing"|"billing"|"case_management"|"infection_control"|"pharmacy",
      "urgency": "critical"|"high"|"medium"|"low",
      "suggestedAction": string,
      "communicationAction": string|null,
      "status": "pending"|"awaiting_response"|"needs_clinical_review"|"resolved"|"admission_blocker",
      "activityLog": [{ "timestamp": string, "event": string }]
    }]
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
- For followUp.actions: generate one action per issue detected. Assign realistic owner, urgency, suggested action, and communicationAction. activityLog should show "Issue detected" as first entry.
- admissionReadiness: "pending_critical" if any critical unresolved issues exist; "high_risk" if patient is medically complex AND has critical blockers; "ready_with_warnings" if only warnings remain; "ready" if all clear.
- issues.id = "i1","i2"... medications[].id = "m1","m2"... reconciliation.medications[].id = "r1","r2"... alerts[].id = "ra1","ra2"... recommendedActions[].id = "ac1","ac2"... followUp.actions[].id = "f1","f2"...`
