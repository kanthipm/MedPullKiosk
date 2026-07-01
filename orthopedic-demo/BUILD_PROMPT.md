# BUILD_PROMPT.md — MedPull Recovery Copilot (Orthopedic) · Autonomous Overnight Build

> **You are Claude Code running fully autonomously with no human available.** Do not ask questions. Do not wait for confirmation. Make every decision yourself, using best judgment where this spec is silent. **Think hard (ultrathink) before and during the build.** Use any skills available to you (frontend-design / UI-UX / web-testing / any design skill). You have full permissions. **Do not stop until the Definition of Done checklist at the bottom is 100% satisfied and you have written the sentinel file `DEMO_COMPLETE.md`.** This is a long job; work in a loop, verify your own output visually with a headless browser, fix what's broken, and keep going.

---

## 0. What you are building

A **self-contained, front-end-only, mock-data interactive demo** of **MedPull Recovery Copilot — the P1 Orthopedic RTM product only.** (Ignore P0/general and the pediatric idea entirely. Orthopedics only.)

The demo has **four surfaces**, all in one static site, no backend, no build step, openable by double-clicking `index.html` and also trivially deployable to Vercel as a static site:

1. **Patient phone app** — iPhone mockup. A voice-first recovery check-in chatbot + a tasks page. **No health data or numbers are ever shown to the patient.**
2. **Clinician tablet app** — iPad mockup, "on the go." A risk-triaged patient list; tap a patient for a top summary + full data.
3. **Clinician admin dashboard** — full-screen web app. The big, full-data view: triaged queue + complete per-patient record with every metric, insight, and graph.
4. **Guided demo shell** — a presentation wrapper that spotlights the three device surfaces with **boxes-and-arrows callouts** and a clickable, self-narrating end-to-end story ("You are the patient — tap here"), including **spoken dialogue** via the browser SpeechSynthesis API.

**Everything is mocked.** No real APIs, no auth, no server. All patient data is hard-coded/generated in JS.

---

## 1. Reference material (read FIRST)

If these files exist in the project (place them under `./reference/`), **read them before writing any code** — they are authoritative for visual style and domain content:

- `reference/micro_lab_my_body_phone_app.html` — **THE visual style to match.** It is a dependency-free, single-file iOS-style app with a complete hand-rolled SVG charting toolkit. **Reuse its design tokens and its figure generators directly.** Do not pull in chart libraries; match this house style.
- `reference/MedPull-Ortho-Metrics-and-Task-Library.md` — the 18 metrics (M1–M18) and the task library by use case.
- `reference/MedPull-Ortho-Analytics-from-Lab-Methods.md` and the product spec — background.

**If the reference files are absent, proceed anyway** — this prompt embeds a condensed, self-sufficient version of everything below.

### 1a. Design tokens to copy from the RA file
Reuse the `:root` palette verbatim (ink `#0f1830`, muted `#6b7793`, line `#eef1f7`, semantic colors heart `#ff4d6d`, move `#ff5a3c`, exer `#1fd17a`, sleep `#6c5ce7`, oxy `#2f80ed`, recov `#00b894`, etc.), the soft radial-gradient background, the rounded-card system (22px radii, soft shadows), the pill/badge styles, the tier-badge system (Everyday/Advanced/Clinical), and the SF-system font stack. **Add a liquid-glass treatment** (translucent `background: rgba(255,255,255,.7)` + `backdrop-filter: saturate(1.4) blur(16px)` + hairline border) for the patient bottom slider, the tab bars, and floating panels.

### 1b. SVG figure toolkit to reuse (and map to ortho metrics)
The RA file already defines these generators. **Copy them into a shared `charts.js` and reuse them.** Map figures → metrics:

| RA figure fn | Renders | Use for ortho metric(s) |
|---|---|---|
| `figControl` | baseline + anomaly/σ windows, control chart | **M1** load control chart, **M18** plateau/regression change-point |
| `figRegression` | response model + residuals scatter | **M2** load→pain slope, **M4** walking economy (HR vs cadence residual) |
| `figIMU` | tri-axial motion + gait bouts | **M3** loading asymmetry, **M5** endurance fade, **M6** sit-to-stand |
| `figBaseline` | baseline ribbon + trend line | **M7** cadence recovery curve, **M17** trajectory vs expected |
| `figDriver` | cross-signal driver matrix (HR/HRV/Sleep/SpO₂/Temp/Act) | **M12** deterioration index, **M13** thermal-cardiac coupling |
| `figStacked` | stacked profile timelines | **M9** nocturnal disruption vs pain |
| `figPoincare`, `figPSD`, `figScalogram` | HRV Poincaré / PSD / scalogram | **M10** autonomic recovery, **M11** circadian (Clinical-tier evidence) |
| `spark`, `barChart`, `gauge`, `calendarHeat` | sparklines, bars, gauges, heat | trend minis, adherence, risk gauges, wear-time coverage (**M16**) |

Also reuse `series()`, `rng()` (seeded RNG for reproducible mock series), `cframe/cgeo/cpath`, the viridis `VIR` colormap, and the `callout()` helper. Extend where needed; keep the aesthetic.

---

## 2. Domain content to embed (source of truth)

### 2a. The 18 metrics (each is a "report object": *status vs personal baseline → finding → evidence graph → confidence → next step*). Framed as **monitoring, not diagnosis.**
- **M1 Acute:Chronic Load Ratio** (personalized floor/ceiling band). Overreach vs under-loading.
- **M2 Load–Pain Sensitivity slope** — next-day pain per unit load; flattening = tolerance improving.
- **M3 Loading Asymmetry decay curve** — unilateral recovery; plateau = flag.
- **M4 Walking Economy** — HR cost per unit gait output; rising = flag.
- **M5 Endurance-Fade Index** — intra-walk cadence/speed decay.
- **M6 Sit-to-Stand frequency & rise tempo** — chair-rise proxy.
- **M7 Cadence Recovery curve** — preferred cadence vs baseline.
- **M8 Stair reintroduction & flight tolerance.**
- **M9 Nocturnal Disruption pain-proxy** — overnight restlessness/WASO vs evening pain.
- **M10 Autonomic Recovery trend** — overnight HRV + RHR baseline deviation.
- **M11 Circadian Rest-Activity amplitude** — rhythm consolidation.
- **M12 Multi-Signal Deterioration Index** — Mahalanobis deviation of RHR+RR+temp+HRV+SpO₂ from personal baseline. **"Signals deviating from baseline — recommend review," never "detected."**
- **M13 Thermal-Cardiac coupling** — temp+RHR rise together w/ HRV drop → infection-watch. Same guardrail.
- **M14 Verified-Adherence rate** — % of tasks the data confirms.
- **M15 Disengagement-Risk score.**
- **M16 Data-Confidence / coverage index** — wear-time + sync freshness; gates every other metric.
- **M17 Recovery-Trajectory fit & expected-curve deviation** — "on track / behind / ahead" with CI band.
- **M18 Plateau / regression change-point alert.**

### 2b. The 6 orthopedic profiles (group the metrics into these cards on the dashboard)
1. **Prehab Readiness** (pre-op baseline) · 2. **Functional Reserve Drift** (slow decline) · 3. **Load Tolerance** (safe-load envelope; M1/M2) · 4. **Home Independence Readiness** (stairs, walking bouts, transfers; M6/M8) · 5. **Lab-Wearable Ortho Context** (labs explain unstable recovery) · 6. **Clinic Exception Queue** (the triage: *stable / drifting / missing data / needs review*).

### 2c. Guardrails (NON-NEGOTIABLE — bake into UI copy)
- **"Monitoring signals for clinician review — not a diagnosis"** banner on every clinician analytics view.
- **Be vague/qualitative on raw measurement.** Wearable-derived numbers must be shown as **directional trends, deltas, and qualitative bands with a confidence/coverage tag** — NOT as precise clinical measurements. (e.g., "Steps trending down ~40% vs personal baseline · Med confidence" not "3,412 steps".) Every wearable metric carries a small **"derived estimate"** + confidence chip.
- **Patient side shows ZERO numbers/stats/graphs.** Qualitative encouragement only.
- **"Text message to doctor if high risk"** — when a patient crosses into High risk, fire a mock SMS-to-clinician animation/toast in the demo.

### 2d. Mock patient roster (triage must be meaningful — mix of tiers & a low-data case)
Sort order on both clinician surfaces: **🔴 High/needs-review → 🟡 needs-attention → ⚪ missing-data → 🟢 stable.**

| Patient | Procedure | Post-op day | Risk | One-line "why flagged" | Data conf |
|---|---|---|---|---|---|
| **Marcus Reyes** (HERO) | Total Knee (TKA) | 8 | 🔴 High | Pain ↑ 4d · new fever · temp+HR coupled | High |
| Linda Park | Rotator cuff | 10 | 🟡 Medium | Night-pain proxy ↑ (sleep fragmented) | Med |
| Robert Ellison | Lumbar decompression | 6 | 🟡 Medium | Sitting tolerance low · walking improving | Med |
| Sofia Marchetti | Ankle fracture (WB progression) | 21 | 🟡 Medium | Loading asymmetry high (expected) · DVT watch | Med |
| Aisha Okonkwo | Total Hip (THA) | 15 | 🟡 Medium | Walking symmetry plateaued · steps ↓ | Med |
| Priya Nair | Total Hip (THA) | 9 | ⚪ Missing data | Low watch wear · confidence low | Low |
| David Chen | ACL reconstruction | 34 | 🟢 Low | On track · running reintroduced | High |
| James Whitfield | Total Knee (TKA) | 40 | 🟢 Low | Recovery ahead of curve | High |
| Elena Vasquez | Meniscus repair | 19 | 🟢 Low | On track | High |

### 2e. HERO patient full data — **Marcus Reyes, TKA, day 8, HIGH.** Build his complete record:
- **Check-in (patient-reported):** pain rising 4 days, worse in evenings; a new "feverish" feeling; knee feels warmer and more swollen; sleeping poorly.
- **Wearable-derived (qualitative + trend series via `series()`):** steps trending down ~40% vs his baseline; a **load spike on day 6** (over-did it) then a crash; walking asymmetry plateaued elevated; sleep fragmentation up; resting HR drifting up; wrist temperature up; HRV suppressed.
- **Metric flags to render:** M1 (spike→drop on control chart), M2 (pain-cost slope steepening), M3 (asymmetry plateau), M9 (nocturnal disruption ↑), M10 (HRV/RHR suppressed), **M12 (deterioration index elevated), M13 (thermal-cardiac coupling)**, M17 (behind expected curve), M18 (change-point at day 6).
- **Quick appointment summary (top of clinician views — 3–5 lines):**
  > *Day 8 TKA. Recovery has deviated from the expected trajectory since day 6. Reported pain rising over 4 days with a new fever sensation and a warmer, more swollen knee. Overnight temperature and resting heart rate have risen together with suppressed HRV and worsening sleep — a coupled pattern that, with the reported fever and local warmth, warrants review for possible surgical-site infection. Steps and walking symmetry have plateaued. **Recommended:** nurse outreach today, assess the wound, consider bringing the follow-up forward. (Monitoring signals for clinician review — not a diagnosis.)*
- **5 tasks the clinician assigns Marcus** (these appear on his phone Tasks page):
  1. Wear your watch overnight · 2. Log knee pain, morning & evening · 3. Take 3 short walks spread across the day · 4. Take a photo of your incision (bring forward — today) · 5. Stay within today's gentle activity range (don't over-do it).

Give every other roster patient enough mock data that clicking them shows a coherent (if lighter) record. Use the seeded RNG so series are stable across reloads.

---

## 3. Surface specs

### 3.1 Patient phone app (`patient` surface)
- iPhone frame (~390×844) with Dynamic-Island, status bar — reuse the RA `.device/.screenframe/.island/.statusbar` shell.
- **Home = voice check-in chatbot** (like the MedPull kiosk). Center it on a large animated **listening orb / mic**. Voice-first: a big talk button; when "listening," animate; use **SpeechSynthesis (TTS)** to speak the assistant's lines aloud. Render the exchange as chat bubbles.
  - Scripted-but-natural conversation for the demo. The assistant: runs the daily check-in; on request gives a **qualitative** recovery summary ("You've been putting in the work — let's take it a little easier today," **never numbers**); lets the patient **report a symptom/issue** (logs it; if it pushes them to High, trigger the SMS-to-doctor demo); explains **how to reach their clinician** and lets them **request/schedule** an earlier visit.
- **Bottom = liquid-glass left/right segmented slider:** **Chat ⟷ Tasks.** Glass look (blur + translucency). 
- **Tasks page (right):** the **5 assigned tasks** as clean cards with a title, a friendly one-line "why," and **qualitative completion only** (checkmark that auto-ticks, or a non-numeric progress ring). No stats. A gentle streak/encouragement is fine; no charts.
- Calm, modern, reassuring. Gentle disclaimer where natural ("I'm here to help you recover — for anything urgent, contact your care team.").

### 3.2 Clinician tablet app (`tablet` surface)
- iPad frame, "on the go." 
- **Triage list** of all roster patients in the sort order above. Each row: name, procedure, "Day N", risk pill, the one-line why-flagged, a **data-confidence chip (M16)**, last check-in. High rows visually loudest.
- **Tap a patient →** detail view: **the quick appointment summary pinned at the very top** (the "read this while the patient is in front of you" block), then the full data (metrics as report objects, the profile cards, the graphs) scrollable below. 
- Quick actions bar: **Message patient · Assign task · Escalate/Nurse triage · Bring follow-up forward.** Wire "Assign task" so it can push Marcus's 5 tasks to the phone surface in the demo.

### 3.3 Clinician admin dashboard (`dashboard` surface)
- Full-screen web layout. **Left rail = Clinic Exception Queue** bucketed *Needs review / Drifting / Missing data / Stable*, patients triaged within. Top-of-rail: counts per bucket.
- **Main = full per-patient record** on select:
  - **Top: the quick appointment summary** (same block/purpose).
  - Then **everything**, organized by the 6 profiles: each metric as a report object (status vs baseline · finding sentence · **evidence graph from the reused figure toolkit** · confidence chip · next-step line). Include the **recovery-trajectory-vs-expected** chart (M17), the **deterioration index / driver matrix** (M12/M13), conversation history, **task adherence (M14)**, and an **RTM documentation summary** block.
  - A **tier toggle (Everyday / Advanced / Clinical)** like the RA file, revealing progressively deeper evidence (Clinical shows Poincaré/PSD/scalogram-style panels + method provenance + limitations).
  - **"Monitoring, not diagnosis"** banner + "derived estimate/confidence" chips throughout.

### 3.4 Guided demo shell (`index.html` landing / `demo` surface) — the star
A full-screen stage that presents the three device mockups and drives a **clickable, self-narrating story** with **boxes-and-arrows callouts** overlaid (animated SVG connectors + labeled hint bubbles: "① You are the patient — tap the mic", etc.). Progression is by clicking the highlighted hotspot; each step advances the narrative and can **speak** its key line via TTS.

**The end-to-end narrative (make the dialogue *mean* something — patient words must match the data):**
1. **Patient (phone):** Marcus opens the app, taps the mic, does his check-in. He reports the pain rising for days, a new feverish feeling, a warmer/more swollen knee, poor sleep. Assistant responds empathetically (spoken), logs it.
2. **Risk escalation:** the check-in + trend push Marcus to **High** → fire the **SMS-to-clinician** toast ("⚠️ High-risk change — Marcus Reyes"). He jumps to the top of the triage list.
3. **Clinician (tablet, on the go):** opens Marcus, reads the **quick summary**, glances at the graphs — pain ↑, steps ↓, sleep fragmented, load spike→drop, **temp+HR coupled / deterioration index up**, trajectory behind curve.
4. **Action:** clinician **assigns the 5 tasks**, **messages** Marcus, and **brings the follow-up forward** / escalates to nurse triage.
5. **Back to patient (phone):** the **5 tasks appear** on his Tasks page; he gets a gentle nudge; assistant confirms the earlier appointment.
6. **"At the appointment" (dashboard):** show the **full dashboard** with the appointment summary pinned on top — "this is what your surgeon sees when you walk in," plus the on-the-go tablet framing woven in.

Include simple **prev/next controls** and a "restart" so it can be presented live, and an autoplay option. Keep transitions smooth (CSS transitions, gentle spring easing).

---

## 4. Tech & structure
- **Vanilla HTML/CSS/JS, no framework, no build step, no external CDNs** (match the RA file; maximizes portability and "just open it"). All SVG charts hand-rolled via the reused toolkit.
- Suggested layout (you may refine):
  ```
  index.html            # guided demo shell (landing)
  patient.html          # patient phone app  (also embeddable via iframe in the shell)
  tablet.html           # clinician tablet
  dashboard.html        # clinician admin dashboard
  /assets/charts.js     # reused SVG figure toolkit (from RA file) + ortho metric renderers
  /assets/data.js       # mock roster + hero patient full data (seeded)
  /assets/ui.js         # shared components (cards, pills, report-object, tier toggle, glass slider)
  /assets/styles.css    # shared tokens + components (from RA :root) + liquid-glass
  DECISIONS.md          # running log of decisions & assumptions you made
  ```
  The demo shell may embed the three surfaces via iframes for isolation, or render them as in-page "device" components — your call; pick whichever renders more reliably.
- Use a **shared mock "event bus"** (a tiny JS pub/sub, or `postMessage` across iframes / `localStorage` events) so the demo can push "task assigned" from the clinician surface to the patient surface live. (Note: the RA file avoids storage; for THIS cross-surface demo, in-page state or `postMessage` is preferred over `localStorage`.)
- Make it responsive enough to present on a laptop; the device mockups are fixed-size, the dashboard is fluid.

---

## 5. Your working loop (follow this)
1. **Ultrathink & plan.** Read the reference files. Write `PLAN.md` with the architecture and a task checklist mirroring §6. Write `DECISIONS.md` and keep appending as you choose things.
2. **Scaffold** the files and shared styles/tokens; port the RA charting toolkit into `charts.js`; build `data.js` (roster + hero).
3. **Build each surface** in this order: dashboard → tablet → patient → demo shell. (Dashboard first because it exercises the most components.)
4. **Self-verify after each surface.** Launch a headless browser (Playwright/puppeteer — install if needed) and screenshot at the right viewports (dashboard ~1440×900; tablet ~1024×768; phone ~390×844; demo shell ~1440×900). **Open the screenshots and actually look.** Check the browser console for errors. Fix layout breaks, overflow, broken SVGs, dead buttons. Iterate until each surface looks polished and works.
5. **Wire the demo narrative** end-to-end; click through every step programmatically and by screenshot; confirm the cross-surface task push, the SMS toast, the TTS calls, and the boxes-and-arrows all fire in order.
6. **Full acceptance pass** against §6. Fix everything. Re-screenshot. Repeat until all pass.
7. **Only then** write `DEMO_COMPLETE.md` summarizing what was built, how to open it, and the demo click-path. Stop.

**Persistence rule:** if something is broken or unpolished, you are not done. Do not stop to report progress or ask anything — keep fixing and building until §6 fully passes.

---

## 6. Definition of Done (must ALL be true before writing `DEMO_COMPLETE.md`)
1. `index.html` opens with no console errors and presents the guided demo shell.
2. All four surfaces render and are reachable; no broken layouts or overflow at their target viewports (verified by screenshots you have viewed).
3. **Patient app:** voice-first check-in chatbot works, speaks via TTS, shows chat bubbles, supports reporting a symptom + requesting an appointment; **contains zero numbers/stats/graphs**; bottom **liquid-glass Chat⟷Tasks** slider works; Tasks page shows the **5 hero tasks** with qualitative completion only.
4. **Tablet app:** triaged patient list in the correct order; tap-through to a detail view with the **quick summary pinned at top** and full data below; quick-action bar present and wired.
5. **Dashboard:** exception-queue rail + full per-patient record with **all applicable metrics as report objects**, grouped by the 6 profiles, each with a **reused-toolkit graph**, confidence chip, and next-step; **tier toggle** works; trajectory-vs-expected, deterioration/driver, conversation, adherence, and RTM summary all present.
6. **Guided demo:** the full 6-beat narrative runs via clickable hotspots with **boxes-and-arrows callouts**; the cross-surface **task push** works (assigning on clinician side makes tasks appear on the phone); the **high-risk SMS-to-clinician** toast fires; dialogue is coherent and matches the hero's data; prev/next/restart work.
7. **Guardrails visible:** "monitoring, not diagnosis" banner on clinician analytics; wearable metrics shown as qualitative trends + deltas with **confidence/"derived estimate" chips** (not precise measurements); M12/M13 phrased as "signals deviating from baseline — recommend review."
8. **Visual quality:** matches the RA Micro-Lab aesthetic (tokens, cards, pills, tier badges) with liquid-glass accents; looks modern and polished, not template-y. You have viewed screenshots and are satisfied.
9. Runs with **no backend, no external network**, fully from local files; also valid as a static Vercel deploy.
10. `PLAN.md`, `DECISIONS.md`, and `DEMO_COMPLETE.md` exist; `DEMO_COMPLETE.md` documents the exact click-path for presenting the demo.

Begin now. Ultrathink first, then build. Do not stop until done.
