# TODOS - MedPull Kiosk

## Deferred from CEO Review (2026-04-08)

### Conversation Replay for Flagged Fields
- **What:** Link each flagged field to the relevant conversation turns so clinic staff can see the exact exchange that produced the flag
- **Why deferred:** Nice audit trail but not critical for pilot. Conversation history is stored in Room DB regardless, so the data exists. This is a UI/query layer on top of existing data.
- **When to revisit:** After pilot ships and clinic staff give feedback on the dashboard. If they ask "why was this flagged?", build it.
- **Effort:** S

### Offline Mode / Local Queue + Sync
- **What:** Queue conversation turns locally when tablet loses connectivity, sync when back online
- **Why deferred:** Pilot requires connectivity. Community health centers generally have WiFi. Not worth the complexity for one clinic.
- **When to revisit:** When expanding to clinics with unreliable connectivity.
- **Effort:** M

### Voice-First Input
- **What:** Speech-to-text as the primary input method for low-literacy patients
- **Why deferred:** Text + tap is sufficient for pilot. Existing STT infrastructure exists in the app but needs UX work for conversational flow.
- **When to revisit:** After pilot, based on observation of patients struggling with text input.
- **Effort:** S

### AI-Learned Skip Patterns
- **What:** AI analyzes aggregate patient responses across sessions to learn which skip patterns work best, rather than using hand-provided rules
- **Why deferred:** Need data first. Coastal Gateway gets hand-provided decision logic. After N hundred sessions, patterns emerge.
- **When to revisit:** After 500+ completed intakes across at least 2 form types.
- **Effort:** L

### SQLCipher / Encrypted Room DB
- **What:** Encrypt Room database at rest on tablet
- **Why deferred:** Pilot has no long-term patient data on device (destructive migration wipes on update). PHI exposure window is one session.
- **When to revisit:** Before multi-clinic rollout. Required for proper HIPAA device compliance.
- **Effort:** S

### Formal Claude Tool-Use API (Approach C Phase 2)
- **What:** Swap manual JSON parsing for Claude's native tool-use API. ActionHandler interface stays the same.
- **Why deferred:** Manual JSON parsing works for pilot. Tool-use upgrade is a parser swap, not a rewrite (Approach C design).
- **When to revisit:** When adding second form type, or if JSON parsing fallbacks fire frequently.
- **Effort:** S

### HIPAA BAA — Grok API Switch
- **What:** Switch the conversation engine from Claude API to Grok API (or Anthropic enterprise) with a signed BAA
- **Why deferred:** Pilot uses Claude API without BAA, documented risk posture with clinic consent addendum. Not a substitute for a real BAA but acceptable for a supervised pilot with one clinic.
- **When to revisit:** Before any second clinic or any unsupervised patient use. This is not optional for production.
- **Effort:** S (API swap, same architecture)

### clarificationCount Persistence Across Process Death
- **What:** Save per-field clarification attempt counts to FormIntakeFlowEntity so staff escalation state survives tablet lock/process death during a session
- **Why deferred:** In-memory is fine for 5-15 minute pilot sessions. Process death mid-session is rare on a supervised kiosk.
- **When to revisit:** Before multi-clinic rollout or if patients report being re-asked the same failed question after session resume.
- **Effort:** S

### ClinicDashboard Extensibility
- **What:** Add search, filter by date/provider, and pagination to the clinic dashboard
- **Why deferred:** Single clinic, small volume. Staff can scroll a flat list.
- **When to revisit:** When second clinic onboards or patient volume exceeds ~200/month.
- **Effort:** S

### Multi-Clinic Portal
- **What:** Per-clinic configuration, schema management, separate dashboards
- **Why deferred:** One clinic for now. Architecture supports it (schema-driven, form-agnostic engine).
- **When to revisit:** When second clinic signs up.
- **Effort:** L
