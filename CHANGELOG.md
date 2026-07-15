# Changelog

## [recovery-copilot 1.2.0] - 2026-07-11

### Added
- **Live LLM connection via local Ollama** — auto-detected at :11434 (native API, JSON-constrained, thinking disabled); provider priority Groq → Ollama → deterministic fallback; every narrative now genuinely regenerates on refresh
- **Ask bar** — natural-language questions over the roster with a retrieve → per-patient verify → compose pipeline (prevents cross-patient fact attribution by small local models); answers cite and filter to matching patients
- **Draft with AI** — grounded, editable patient-message drafts in the Message modal
- **Startup insight warmer** — caches generate in a background thread so first loads never block on a cold model; stable patients keep deterministic worklist reasons for speed

## [recovery-copilot 1.1.0] - 2026-07-11

### Changed
- **Console redesigned onto the orthopedic-demo design system** — liquid-glass chrome, gradient canvas, demo risk tokens (rpills, status accents, triage rows), Inter with the demo's weight hierarchy
- **Care action bar restored** — Assign tasks (persists to the patient plan), Message (queued stub until SMS integration), Escalate (in-app care-team notification), each with glass modals and toasts
- **Everyday / Advanced / Clinical tier toggle restored** — gates supporting-signal depth per the original demo
- **Refresh analysis now performs a true refresh** — engine rerun + narrative-cache bust (fresh LLM generation) with shimmer loading overlays across every card
- Micro-animations throughout: staggered card entrances, hover lifts, modal/toast transitions, animated disclosure — all gated by prefers-reduced-motion

## [recovery-copilot 1.0.0] - 2026-07-11

### Added
- **Recovery Copilot Provider Console** (`recovery-copilot/`) — the production-track provider web app for post-surgical recovery monitoring, replacing the orthopedic-demo dashboard a physician flagged as too cluttered
- **Recovery Intelligence Engine** — real statistics in Python (EWMA control charts, CUSUM drift, per-procedure expected recovery curves, trajectory + change-point detection, multi-signal composite index, data-confidence gate, risk tiers with typed reason codes)
- **AI narrative layer** — Groq (free tier, optional) with deterministic fallback; strict JSON contracts, banned diagnostic-language validation, guardrail-sentence enforcement, input-hash caching
- **Provider Worklist + Patient Detail UI** — AI daily briefing, prioritized roster, summary-first patient pages with progressive disclosure (React 19 + Vite + Tailwind)
- **Wearable integration scaffolding** — provider-agnostic connector interface, idempotent webhook ingestion (`/api/webhooks/wearables/{provider}`), normalized observation store, per-provider capability map (Apple-only gait metrics), documented Terra/Junction/HealthKit stubs
- **RTM coverage tracking** (16-of-30 monitoring-day windows), in-app high-priority notifications with SMS/email channel stubs
- **Deterministic 10-patient seed** with golden-tier tests (44-test backend suite)

## [1.2.0] - 2026-05-05

### Added
- **Program selection screen** — patients choose between Sliding Fee Eligibility and Medical Intake on entry
- **Sliding Fee Eligibility intake flow** — full 9-field schema (demographics, household size, income sources, employment, insurance status)
- **Back navigation with value preservation** — tapping back steps to the previous question; existing answers are pre-populated, not erased
- **Back from review returns to last field** — IntakeReview "back" lands on the last answered field for easy edits
- **Address auto-fill** — entering a full address in the street field intelligently skips city/state/zip prompts
- **Field normalization** — all parsed values are deterministically formatted: dates → MM/DD/YYYY, phones → (XXX) XXX-XXXX, ZIP → 5 digits, states → 2-letter code, names/city/street → Title Case
- **Groq API fallback** — automatic fallback to Groq (llama-3.3-70b-versatile) when primary Grok API is unavailable
- **Signature capture screen** — `SignatureCapture.kt` composable for drawn signatures
- **PDF form preview** — `FilledFormPreviewScreen` with print and send actions post-submission
- **PDF fallback generation** — `createFormattedSummaryPdf()` handles forms without a matching PDF template

### Fixed
- "Could not generate PDF file" error for the Sliding Fee form (missing template now falls through to summary PDF)
- Address parser using wrong field IDs for `alsoFills` when prefix didn't match expected pattern
- Duplicate companion object compile error in `IntakeConversationEngine`
- Back button exiting intake instead of navigating to previous question

### Changed
- Welcome screen now routes to Program Selection (not directly to Form Selection)
- Login/register/verify success navigates to Program Selection
- `IntakeConversationEngine` refactored to stateless single-field question/parse API

## [1.1.0] - 2026-04-11

### Added
- Typeform-style one-question-at-a-time intake UI
- Floating chat sidebar FAB
- Coastal Gateway form with 50 fields and complete skip logic
- Consent batch review panel
- Demo mode with Medicaid form, save progress, prefill, PDF export
- Filled form PDF preview with print/send after review submit
- AI model routing: Grok-3-mini for conversation, Groq fallback
- FHIR R4 integration (HAPI FHIR 7.4.0) with neutral healthcare adapter layer
- AWS Cognito auth, S3 storage, Textract OCR, Translate
- HIPAA audit logging

## [1.0.0] - Initial release
