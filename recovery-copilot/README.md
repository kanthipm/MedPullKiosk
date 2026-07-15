# MedPull — Recovery Copilot · Provider Console

The provider-facing web application for post-surgical recovery monitoring.
It answers one question for the care team, every morning:

> **Who needs my attention today, and why?**

AI summaries and prioritization come first; supporting stats stay behind
progressive disclosure. Every narrative on screen carries the product
guardrail — *monitoring signals for clinician review, not a diagnosis* — and
that phrasing is enforced in code, not just in prompts.

## Quickstart

Requirements: [uv](https://docs.astral.sh/uv/) and Node 20+.

```bash
make setup     # install backend (uv) + frontend (npm) dependencies
make seed      # create + populate the demo database (10 patients, ~2,800 observations)
make dev       # API on :8000 + Vite dev server on :5173
```

Single-process demo (serves the built frontend from FastAPI):

```bash
make build && make run     # everything on http://localhost:8000
```

`make test` runs the backend suite (seed determinism, engine golden tiers,
guardrail enforcement, API contracts, connector idempotency).

### LLM setup

Provider priority: **Groq → local Ollama → deterministic fallback.**

- **Ollama (zero-config default):** if Ollama is running on the machine with
  the configured model pulled (`qwen3-vl-agent:latest` by default — override
  with `OLLAMA_MODEL`), the app uses it automatically. Calls go through
  Ollama's native API with `format: json` and thinking disabled — the same
  local-LLM architecture as the MedPull kiosk.
- **Groq:** set `GROQ_API_KEY` in `.env` (free tier at console.groq.com) for
  faster cloud generation.
- **No LLM at all:** the deterministic engine renders narratives from typed
  reason codes; the UI labels them "rules-based".

All LLM output is validated (JSON contracts, banned diagnostic language,
guardrail sentence) and silently replaced by the deterministic version on any
violation. Insight caches warm in a background thread at startup so the first
page load never waits on a cold model.

### AI features

- **Recovery summaries, worklist reasons, suggested actions, daily briefing**
  — regenerated whenever the data (or a Refresh) changes them.
- **Ask bar** — natural-language questions over the roster ("Who reported
  fever this week?", "Which knee patients are struggling?"). Answers use a
  retrieve → per-patient verify → compose pipeline so a small local model
  can't attribute one patient's symptoms to another, and the worklist filters
  to the cited patients.
- **Draft with AI** — one click drafts a patient message grounded in their
  analytics and their own check-in words; always editable, never auto-sent.

## Architecture

```
                    ┌────────────────────────────────────────────┐
 wearables ── webhooks ──► connectors/ ──► observations (canonical store)
 (mocked in v1)     │            │                               │
                    │   Recovery Intelligence Engine (engine/)   │
                    │   baselines · EWMA/CUSUM deviation ·       │
                    │   expected recovery curves · trajectory ·  │
                    │   composite index · confidence gate ·      │
                    │   adherence · risk tiers + reason codes    │
                    │            │                               │
                    │   LLM layer (llm/): Groq or deterministic  │
                    │   fallback · validated · cached            │
                    │            │                               │
                    │   REST API (api/) ──► React console        │
                    └────────────────────────────────────────────┘
```

- **backend/** — FastAPI + SQLAlchemy 2 + SQLite. numpy/scipy/pandas power the
  analytics; the engine stores one `RiskAssessment` per run and recomputes
  lazily when the observation set changes (input-hash staleness check).
- **frontend/** — React 19 + Vite + TypeScript + Tailwind, styled on the
  MedPull liquid-glass design system (gradient canvas, glass chrome, the
  orthopedic-demo risk tokens). Two primary surfaces: the Worklist
  (prioritized roster + AI daily briefing) and the Patient Detail (AI recovery
  summary, care actions, timeline, check-in conversations, tiered supporting
  signals).

  - **Care actions** — Assign tasks (persists to the patient's plan), Message
    (queued until SMS goes live), and Escalate (notifies the care team) live
    in a glass action bar on every patient record.
  - **Signal depth tiers** — the demo's Everyday / Advanced / Clinical toggle
    controls how much evidence renders: Everyday keeps signals collapsed,
    Advanced opens trajectory + metric cards, Clinical adds next steps and the
    full deviation panel.
  - **Refresh analysis** — reruns the engine, busts the narrative caches so
    the LLM genuinely regenerates every summary, and covers each card with
    shimmer loading bars until the new analysis lands.

### The intelligence engine

Two layers, deliberately separated:

1. **Deterministic analytics** (`app/engine/`) — every number on screen comes
   from here. Vitals are judged against the patient's own pre-op baseline
   (EWMA control charts + CUSUM drift); activity metrics are judged against a
   per-procedure **expected recovery curve**, so a day-8 knee patient walking
   40% of baseline is *normal*, not a finding. A coverage-based confidence
   score gates everything — too little device data yields "Missing data",
   never a false "Stable". Output: a risk tier plus typed reason codes.
2. **Narrative layer** (`app/llm/`) — turns the analytics bundle + check-in
   transcripts into the worklist reason, patient summary, suggested actions,
   and roster briefing. Strict JSON contracts, banned-phrase validation
   (`detect…`/`diagnos…`), guardrail-sentence enforcement, cached by input
   hash so nothing is regenerated until the data changes.

### Wearable integration scaffolding

No live integrations ship in v1, by design — but the seams are real:

- `connectors/base.py` — provider-agnostic `WearableConnector` interface
  (authorize / OAuth callback / webhook registration / historical fetch /
  normalize).
- `POST /api/webhooks/wearables/{provider}` — signature-check hook, raw event
  persistence, normalization, **idempotent upsert** by dedupe key (wearable
  providers re-deliver and back-fill as a matter of course), engine recompute.
- `connectors/capabilities.py` — per-provider metric capability map. Gait
  metrics (walking speed/asymmetry/steadiness) are Apple-exclusive; the UI
  degrades per patient device automatically.
- `terra.py` / `junction.py` / `apple_healthkit.py` — documented stubs
  capturing what each production integration requires (BAAs, webhook
  signatures, the iOS companion-app constraint for HealthKit).

Recommended production path (researched mid-2026): one aggregator — **Terra**
(~$399/mo flat, BAA, widest coverage) or **Junction** fka Vital
($0.50/user/mo, bundles labs) — plus a thin Apple HealthKit path for gait.
Avoid: Human API (absorbed into LexisNexis), Metriport (exited wearables),
Google Fit (shut down; Fitbit moves to the Google Health API in 2026).

### RTM scaffolding

`app/rtm/` tracks measurement-days per rolling 30-day window
(CPT 99454-style ≥16-day threshold). It surfaces as one quiet line on the
patient page — an architectural capability, not another dashboard.

## Demo roster

Seeded deterministically (`app/seed/`): 10 patients across 7 orthopedic
procedures. Marcus Reyes (TKA day 8) carries a possible-infection signal
pattern — coupled RHR/temperature rise, falling HRV, activity collapse — that
exercises every part of the engine, including the high-priority notification
path. Priya Nair's barely-worn watch exercises the missing-data gate.

Demo webhook (full ingestion path against the seeded DB):

```bash
curl -X POST localhost:8000/api/webhooks/wearables/mock \
  -H 'Content-Type: application/json' \
  -d '{"patient_id":"james","provider":"fitbit","records":[{"metric_type":"steps","date":"2026-07-11","value":9100,"unit":"count"}]}'
```

## Not in v1

Auth (deliberately open for demos), real integrations, SMS/email delivery
(channel stubs record intent), FHIR export, multi-clinic tenancy.
