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
  return {
    hasKey: key.length > 0,
    model: 'gpt-4o',
    provider: 'OpenAI (dev)',
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
    onProgress?.('Sending to AI model…')
    const OpenAI = (await import('openai')).default
    const client = new OpenAI({ apiKey, dangerouslyAllowBrowser: true })
    const combined = fileTexts.map((f) => `=== FILE: ${f.name} ===\n${f.text}`).join('\n\n')
    const completion = await client.chat.completions.create({
      model: 'gpt-4o',
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
  return localStorage.getItem('snf_openai_key') ?? ''
}

export function saveApiKey(key: string) {
  localStorage.setItem('snf_openai_key', key)
}

// ─── System prompt (kept in sync with SNFBridge.kt) ─────────────────────────

const SYSTEM_PROMPT = `You are an expert SNF admissions coordinator AI assistant. Analyze hospital transfer documents and extract structured patient information.

Return ONLY a valid JSON object — no markdown, no prose outside JSON.

Schema:
{
  "snapshot": { "name": string|null, "dob": "YYYY-MM-DD"|null, "age": number|null, "mrn": string|null, "admittingDiagnosis": string|null, "diagnoses": string[], "dischargeDestination": string|null, "adlStatus": string|null, "attendingProvider": string|null },
  "clinical": { "summary": string|null, "mobilityStatus": string|null, "rehabNeeds": string|null, "psychiatricRisks": string|null, "fallRisk": "low"|"moderate"|"high"|"unknown", "medicationAdherenceConcerns": string|null, "precautions": string[] },
  "medications": [{ "id": string, "name": string, "dosage": string, "frequency": string, "route": string, "indication": string|null, "alerts": string[], "source": string|null }],
  "insurance": { "payerSource": string|null, "memberId": string|null, "groupNumber": string|null, "authorizationStatus": "authorized"|"pending"|"denied"|"unknown", "authorizationNumber": string|null, "coveredDays": number|null, "missingInfo": string[], "reimbursementConcerns": string|null },
  "issues": [{ "id": string, "title": string, "description": string, "severity": "warning"|"error", "field": string|null }],
  "risks": { "fallRisk": "low"|"moderate"|"high"|"unknown", "behavioralRisk": "low"|"moderate"|"high"|"unknown", "medicationNoncompliance": "low"|"moderate"|"high"|"unknown", "housingInstability": "low"|"moderate"|"high"|"unknown", "readmissionRisk": "low"|"moderate"|"high"|"unknown" },
  "timeline": [{ "date": "YYYY-MM-DD", "event": string, "facility": string|null }]
}

Rules: Extract ALL medications. Flag every missing required field as an issue. Generate clinical alerts for high-risk meds. Build timeline from all date references.`
