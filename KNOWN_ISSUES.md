# Known Issues — MedPull Kiosk AI Co-Pilot

These are **deliberate testing-phase shortcuts** in the local-Ollama AI co-pilot
(`MedPullKiosk/`). They are production blockers and must be resolved before any
real patient use. Greppable marker in code: `TODO(medpull-PHI)`.

## 1. On-device PHI logging (PRODUCTION BLOCKER)

Every AI co-pilot decision is written to a local Room table, `copilot_audit_logs`
(`data/local/entities/CopilotAuditEntity.kt`, inserted from
`GuidedIntakeViewModel.logCopilot()`).

- The rows include **patient answers (PHI)** — `rawAnswer` and `normalizedAnswer` —
  stored **in plaintext on the device**.
- This table is **intentionally NOT wired to the S3 audit sync** (unlike `audit_logs`),
  so PHI stays on-device for now. This was a deliberate choice for the testing phase,
  **not** the production design.
- Before production, revisit: **storage location, retention policy, encryption at rest,
  access controls, and HIPAA/BAA compliance** for this data. Decide whether these
  records should exist at all, and if so where and for how long.

Search `TODO(medpull-PHI)` to find every site (entity, DAO, and the logging call).

## 2. Medical-advice guardrail is a soft keyword screen (HARDEN BEFORE PRODUCTION)

The co-pilot must never give medical advice. The **primary** defense is the system
prompt in `IntakeConversationEngine.buildCopilotSystemPrompt()`. The **secondary**
defense, `IntakeConversationEngine.looksLikeMedicalAdvice()`, is only a conservative
keyword screen that downgrades a flagged intervention to a safe re-ask.

- A keyword list is easily bypassed and will miss paraphrased advice.
- Before production, harden this: a stronger/validated classifier, an
  output-validation pass, red-team testing, and/or constrained response templates.

## 3. Uncalibrated thresholds (TUNE BEFORE RELYING ON THEM)

`COPILOT_CONFIDENCE_THRESHOLD`, `COPILOT_BRANCH_THRESHOLD`, and
`COPILOT_TIMEOUT_SECONDS` in `Constants.AI` are model-specific **starting values**.
The model's self-reported `confidence` is **not a calibrated probability**. Calibrate
all three from the `copilot_audit_logs` data (confidence vs. correctness; p95/p99
latency) before treating them as meaningful.

## 4. `also_fills` (inferred values) disabled by design

`COPILOT_ALSOFILL_ENABLED = false`. The co-pilot can propose values for fields the
patient did not directly answer (inferred PHI). For the testing phase this is OFF; the
proposals are still logged (`alsoFillsProposed`) so they can be evaluated. Re-enabling
requires deciding how inferred medical data may populate unconfirmed fields.
