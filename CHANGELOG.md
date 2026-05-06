# Changelog

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
