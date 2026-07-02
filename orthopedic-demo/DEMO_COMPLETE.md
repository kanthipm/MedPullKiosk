# DEMO_COMPLETE.md — MedPull Recovery Copilot (Orthopedic) demo

**Status: complete.** All 10 Definition-of-Done items (BUILD_PROMPT §6) are satisfied and were
verified with a headless browser (Playwright/Chromium) over both `http://` and `file://`, with
screenshots viewed for every surface and several demo beats. Zero console errors anywhere.

---

## How to open
- **Double-click `index.html`** (works straight off `file://` — no server, no build step, no network).
- Or serve statically and open `/index.html`:
  `python3 -m http.server 8000` → http://localhost:8000/index.html
- **Deploy:** drop the folder on Vercel (or any static host) as-is — it's pure static files.
- Best experienced with **sound on** — every scripted line is a **pre-generated neural voice clip**
  (Kokoro-82M, generated locally by `tools/generate_voices.py` into `assets/audio/`, ~1.5 MB total):
  a female narrator (`af_heart`), a female copilot (`af_bella`) and a male Marcus (`am_michael`).
  Clips are plain `<audio>` MP3s, so file:// works and nothing leaves the machine at demo time.
  Any line without a clip (i.e. after editing scripted text — re-run the generator) falls back to the
  browser's Web Speech API.

## What was built
A self-contained, front-end-only, mock-data demo of the **P1 Orthopedic RTM product**, four surfaces:

| File | Surface | What it shows |
|------|---------|---------------|
| `index.html` | **Guided demo shell** (the star) | Full-screen stage that walks through the three devices one at a time (crisp), with a narration card, an arrow to the exact element, and a self-narrating ~25-step tour. Controls: Restart + Autoplay. |
| `patient.html` | **Patient phone** (iPhone) | **MedPull-Kiosk-style** voice check-in: one question at a time, an animated mic waveform, and Marcus's answer streaming in as a transcript (copilot = female voice, Marcus = male voice) — no chat bubbles, no option chips. Plus a Tasks page. **Zero numbers/stats/graphs** — qualitative only. |
| `tablet.html` | **Clinician tablet** (iPad, on the go) | Risk-triaged patient list; tap a patient → quick appointment summary pinned on top + full data. Quick-action bar. |
| `dashboard.html` | **Clinician admin dashboard** | Clinic Exception Queue rail + complete per-patient record: all metrics as report objects grouped by the 6 ortho profiles, each with a hand-rolled SVG figure, confidence/"derived estimate" chip, and next-step. Everyday/Advanced/Clinical tier toggle. |

Shared assets: `assets/styles.css` (RA design tokens + liquid-glass), `assets/charts.js` (the ported
RA hand-rolled SVG figure toolkit — no chart libraries), `assets/data.js` (seeded roster of 9 patients +
Marcus's full record), `assets/ui.js` (shared renderers), `assets/bus.js` (postMessage cross-surface
event bus). Authoritative contract in `SPEC.md`; plan in `PLAN.md`; decisions in `DECISIONS.md`.

---

## The demo click-path (present this live)

Open **`index.html`**. It presents **one device at a time**, rendered crisply, with a narration card
and an arrow pointing at the exact element under discussion (each surface rings its own target). The
bottom bar has just two controls: **↻ Restart** and **▶ Autoplay**. To advance manually, **click the
device (or the pulsing hotspot), or press → / space** (← goes back). Each step speaks its line aloud.
Press **Autoplay** to let it run and narrate itself hands-free; **Restart** resets to the beginning.

It **opens with two short backstory cards** ("Recovery doesn't stop at discharge." → "Meet Recovery
Copilot.") that set up the product, then runs a detailed, ~26-step walkthrough of every surface and
workflow, in three acts (the device + narration card stay centered together, so it looks right on any
display width from a laptop to an ultrawide):

- **Patient phone** — the voice-first home; tap-to-talk check-in; Marcus answering in his own words;
  a gentle qualitative recap (no numbers); reporting a symptom; the **feverish + warm/swollen-knee red
  flag that crosses him into High and texts his care team**; asking to be seen sooner; and the
  liquid-glass **Chat ⟷ Tasks** slider with his 5 gentle, qualitative tasks.
- **Clinician tablet** — the risk-triaged board (Marcus now at the top); how to read a triage row;
  opening Marcus to the **pinned appointment summary**; the "monitoring, not diagnosis" banner; the
  quick-action bar; **assigning the plan (which pushes the tasks to his phone)**; the full record grouped by profile.
- **Clinician dashboard** — the **exception queue**; the **missing-data case (Priya)** where the
  confidence index gates every metric; the pinned summary the surgeon reads first; a **composite-deviation
  KPI headline** (deviation index σ · trajectory · data-confidence M16 · verified-adherence M14); the
  **anatomy of a report object**; a **signal-analytics lens switcher** (one wearable stream re-analyzed
  through eight lenses — control chart, trend+residual, load band, PSD, spectrogram, correlation, composite
  T², circadian); **recovery trajectory (M17)**; **complication surveillance (M12/M13)** phrased as
  "signals deviating from baseline — recommend review"; a **Movement & Biomechanics** ortho panel (gait
  symmetry, load band, cadence↔HR, walking-bout endurance, sit-to-stand, multivariate surveillance with
  driver contributions); a **sensor-scope / feasibility** filter; the **Everyday → Advanced → Clinical**
  tier toggle (Clinical reveals Poincaré / power-spectrum / scalogram + method provenance & limitations);
  **verified adherence + RTM**; the check-in history; and the closing "that's the loop" beat.
  *(Dashboard presentation adapted from the RA's MICRO-LAB clinician-console artifact, in our light house style.)*

All cross-surface effects are real: the escalation actually fires the SMS-to-clinician alert, and
assigning tasks on the clinician side actually makes them appear on the phone.

### Drive the surfaces directly (optional, for Q&A)
- **Patient (`patient.html`):** Tap **Start check-in**, then tap the mic to answer; choose a recap or
  **report a symptom** (tap *"I've been feeling feverish"* / *"warm and swollen"* to trigger the
  clinician alert), then ask to be **seen sooner**. Slide the bottom **Chat ⟷ Tasks** control to see
  the 5 tasks (tap a circle to tick one — no numbers anywhere).
- **Tablet (`tablet.html`):** Tap any patient row (Marcus is loudest at the top). Read the pinned
  summary; try **Assign task** (confirms with a toast), **Escalate**, **Bring follow-up forward**.
- **Dashboard (`dashboard.html`):** Pick a patient in the left Exception Queue. Toggle
  **Everyday → Advanced → Clinical** to progressively reveal deeper evidence (Clinical adds the
  Poincaré / power-spectrum / scalogram panels + method provenance & limitations).

---

## Guardrails on display (by design)
- **"Monitoring signals for clinician review — not a diagnosis"** banner on every clinician analytics view.
- Wearable-derived values are shown as **directional trends / deltas / qualitative bands with a
  confidence + "derived estimate" chip** — never precise clinical measurements.
- **M12 (Deterioration Index)** and **M13 (Thermal–Cardiac coupling)** always read as *"signals
  deviating from baseline — recommend review,"* never "detected/diagnosis."
- The **patient surface shows zero numbers, stats, or graphs** — qualitative encouragement only.
- Crossing into **High risk fires a mock SMS-to-clinician** alert.

## Hero patient
**Marcus Reyes — Total Knee (TKA), post-op day 8, HIGH.** Recovery deviated from the expected
trajectory since day 6; rising pain + new fever + warm/swollen knee; overnight temperature and
resting HR rising together with suppressed HRV and worsening sleep — a coupled pattern flagged for
possible surgical-site infection review. Recommended: nurse outreach today, wound check, bring the
follow-up forward. (Monitoring signals for clinician review — not a diagnosis.)
