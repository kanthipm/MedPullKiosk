# MedPull — Recovery Copilot · Provider Console

The provider-facing web application for post-surgical recovery monitoring.
It answers one question for the care team, every morning:

> **Who needs my attention today, and why?**

AI summaries and prioritization come first; supporting stats stay behind
progressive disclosure. Every narrative on screen carries the product
guardrail — *monitoring signals for clinician review, not a diagnosis* — and
that phrasing is enforced in code, not just in prompts.

Product direction: **[SPEC.md](SPEC.md)** — the P1 Remote Therapeutic
Monitoring (RTM) spec that this console is the foundation for.

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
guardrail enforcement, API contracts, connector idempotency, AWS persistence).

## Deploying

```bash
make deploy      # or: ./infra/deploy.sh
```

Puts the backend on AWS Lambda behind CloudFront, sized to sit inside the
always-free tier — steady-state cost is a few cents a month. Runbook, cost
breakdown and the write-concurrency design: **[infra/README.md](infra/README.md)**.

The application code is unchanged by this: `app/aws/` disables itself when
`S3_BUCKET` is unset, so `make dev` behaves exactly as it always did.

### LLM setup

Provider priority (cloud-first): **Groq → deterministic fallback**, with
local Ollama available strictly as an opt-in middle tier. Selection happens
per call from what's configured and reachable (`app/llm/provider.py`); there
is no other provider — no OpenAI, Anthropic, or xAI code path anywhere.

- **Groq (the cloud model):** set `GROQ_API_KEY` in `.env` (free tier at
  console.groq.com). Model: `llama-3.3-70b-versatile` by default — override
  with `GROQ_MODEL`. Calls go to Groq's OpenAI-compatible chat-completions
  endpoint with JSON response format and retry-after-aware 429 handling.
- **No LLM at all:** the deterministic engine renders narratives from typed
  reason codes; the UI labels them "rules-based".
- **Ollama (opt-in, off by default):** set `OLLAMA_URL` explicitly (e.g.
  `http://127.0.0.1:11434`) to slot a local model between Groq and the
  deterministic fallback — model `qwen3-vl-agent:latest` by default, override
  with `OLLAMA_MODEL`. Availability is probed via `/api/tags` (cached 60s);
  calls use Ollama's native API with `format: json` and thinking disabled.

Error handling: a failed Groq call (rate-limit exhaustion, outage) falls back
to the deterministic renderer for that request AND trips a 3-minute Groq
cooldown with a hard per-call deadline, so no request ever hangs on a
provider that just proved unavailable. After the cooldown one probe call
rediscovers Groq automatically. (With Ollama opted in, the cooldown routes to
it instead of the deterministic renderer.)

All LLM output is validated (JSON contracts, banned diagnostic language,
guardrail sentence) and silently replaced by the deterministic version on any
violation. Results are cached by input hash (fallback-provider rows are
retried once a real LLM becomes available), and insight caches warm in a
background thread at startup so the first page load never waits on a cold
model.

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
                    │   LLM layer (llm/): Groq → Ollama →        │
                    │   deterministic fallback·validated·cached  │
                    │            │                               │
                    │   REST API (api/) ──► React console        │
                    └────────────────────────────────────────────┘
```

- **infra/** — the AWS deployment: one CloudFormation stack (Lambda + Function
  URL + CloudFront + S3 + Parameter Store) and the scripts that build and ship
  it. The Lambda adapters live in `backend/app/aws/`.
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
  - **Supporting signals** — all evidence lives behind one dropdown:
    collapsed to a summary line by default, or open with everything in full
    detail (trajectory chart, deviation drivers, metric cards with next
    steps, adherence & monitoring). Metric data loads lazily on first open.
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

### RTM platform (P1)

The **[SPEC.md](SPEC.md)** P1 workflows are implemented end to end:

- **Compliance engine** (`app/rtm/readiness.py`) — deterministic, never the
  LLM: enrollment (CPT 98975), monitoring-day thresholds (98985/98977 via
  `app/rtm/coverage.py`'s 16-of-30 window), treatment-management time +
  live-interaction requirements (98979/98980/98981), per-CPT billing
  eligibility, a suggested next action, and an automatic **Ready to Bill**
  state.
- **Treatment management** — Call / Follow-up / Update plan join the action
  bar; every action auto-logs an interaction and treatment-management time,
  and time on a patient record is quietly tracked as chart review.
- **AI documentation** (`app/llm/documentation.py`) — encounter notes and
  monthly RTM summaries, drafted by the LLM under the same validation +
  deterministic-fallback discipline as insights; providers review and
  approve, and approved documents are never regenerated.
- **UI** — an RTM readiness card on every patient page (monitoring progress,
  enrollment checklist, billing chips, documentation behind a disclosure), a
  monitoring-days chip per worklist row, and a five-number practice overview
  strip (patients, needing review, ready to bill, adherence, estimated
  revenue — demo rates, clearly labeled).

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
