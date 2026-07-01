# SPEC.md — Authoritative contract for the MedPull Ortho demo

> Every build agent MUST conform to this file exactly. It is the single source of truth so
> the four independently-built surfaces stay visually + structurally coherent. Read this in
> full, plus `reference/micro_lab_my_body_phone_app.html` (house style + chart toolkit) and
> `BUILD_PROMPT.md`, before writing code. No external CDNs, no frameworks, no build step.

---

## 1. Design tokens — `assets/styles.css`

### 1a. `:root` (copy VERBATIM from the reference file, then add ortho + glass tokens)
```css
:root{
 --ink:#0f1830; --muted:#6b7793; --line:#eef1f7; --card:#ffffff; --bg1:#eaeefb; --bg2:#e3e9f6;
 --heart:#ff4d6d; --heart2:#ff8fa3; --move:#ff5a3c; --move2:#ff8a5b; --exer:#1fd17a; --exer2:#9bf06a;
 --stand:#00bcd4; --sleep:#6c5ce7; --sleep2:#a29bfe; --stress:#ff9f1c; --stress2:#ffd166;
 --oxy:#2f80ed; --oxy2:#56ccf2; --recov:#00b894; --soft:#f5f7fc;
 /* ortho risk + status */
 --risk-high:#e5484d; --risk-high-bg:#fdecec; --risk-med:#e07b00; --risk-med-bg:#fff4e5;
 --risk-missing:#7c879e; --risk-missing-bg:#f0f2f7; --risk-low:#0a9d57; --risk-low-bg:#e7f8ef;
 --flag:#e5484d; --watch:#e07b00; --ok:#0a9d57;
 /* liquid glass */
 --glass:rgba(255,255,255,.7); --glass-strong:rgba(255,255,255,.82);
 --glass-border:rgba(255,255,255,.6); --glass-shadow:0 14px 34px rgba(20,30,60,.16);
}
```
Body background = the RA soft radial-gradient + `linear-gradient(160deg,var(--bg1),var(--bg2))`.
Font stack = `-apple-system,BlinkMacSystemFont,"Segoe UI",Inter,Roboto,Arial,sans-serif`.

### 1b. Component classes to provide (reuse RA names; extend, don't rename)
- Cards: `.card` (22px radius, soft shadow), `.card.tight`, `.card h2`, `.card .sub`, `.sec-title`.
- Layout: `.row .between .grid2 .grid3`, `.kv/.k/.v`, `.legendrow`.
- Pills/badges: `.pill` (+ `.good .warn .info .prem .clin`), `.badge-tier` (+ `.prem .clin`).
- Metric mini: `.metric .top .ico .val .delta` (delta `.up/.down/.flat`).
- Report objects: `.report-out`, `.report-figs`, `.rc-chips`, `.chip-in`, `.rc-methods`, `.rc-anchors`, `.gate-row`, `.fam-pills/.fam-pill`, `.kicker`, `.block-label`.
- Figures: `.fig` (rounded, `width:100%`, block), `.clin-fig`.
- Tier toggle: `.segment`/`.segment button(.on)` + body classes `.tier-1/.tier-2/.tier-3` and `.gate.t2/.gate.t3`, `.t1only` gating (copy the RA `.gate` rules verbatim).
- Devices: `.device`(phone 392×850, 56px radius), `.screenframe`, `.island`, `.statusbar`, `.appbar`, `.screens`, `.tabbar`(glass), `.fab`. ADD `.device.tablet` (iPad ~1024×768 landscape, 34px radius, thicker bezel).
- **Liquid glass:** `.glass{background:var(--glass);backdrop-filter:saturate(1.4) blur(16px);-webkit-backdrop-filter:saturate(1.4) blur(16px);border:1px solid var(--glass-border);box-shadow:var(--glass-shadow)}`. Apply to the patient bottom slider, tab bars, floating panels, toasts.

### 1c. New ortho components (add these classes)
- **Risk pill** `.rpill` (+ `.high/.med/.missing/.low`) → colored bg using the risk tokens; loud for high.
- **Confidence / derived-estimate chip** `.confchip` (+ `.high/.med/.low`): small pill reading e.g. `◔ derived · Med confidence`.
- **Monitoring banner** `.mon-banner`: full-width soft-amber hairline banner, text "Monitoring signals for clinician review — not a diagnosis."
- **Quick summary block** `.quick-sum` (pinned, subtle gradient card, left accent bar in risk color).
- **Triage row** `.triage-row` (+ `.high` loudest): grid of name / procedure+day / risk pill / why-flagged / conf chip / last-checkin; hover raise.
- **Exception-queue rail** `.queue-rail`, `.queue-bucket`, `.queue-count`.
- **Dashboard shell** `.dash`, `.dash-rail`, `.dash-main` (fluid; two-column at ≥1100px).
- **Chat bubbles** `.bubble` (+ `.me/.bot`), `.chat-orb` (animated listening orb), `.talkbtn`.
- **Glass segmented slider** `.seg-slider` (Chat⟷Tasks): two segments + sliding glass thumb.
- **Toast** `.toast` (glass, top-right, slide-in) for the SMS-to-clinician animation.
- **Demo callouts** `.callout-bubble` (numbered hint bubble ①②③), `.hotspot` (pulsing ring), and an SVG arrow layer `.callout-svg` (absolute, full-stage, `pointer-events:none`).

---

## 2. Chart toolkit — `assets/charts.js`

Port the RA toolkit VERBATIM (it's dependency-free, hand-rolled SVG). Namespace everything on
a global `Charts` object AND keep the internal helpers module-local. Required members:

**Core helpers (verbatim from RA):** `rng(seed)`, `esc(s)`, `uid()`, `series(rng,n,base,amp,drift,sd)`
(parameterize on an rng arg instead of a global `R`), `grad/pol/arc`, `cgeo/cframe/cborder/cpath`,
`gss`, `VIR`/`vir`, `svgWrap`, `callout(x,y,text,c)`.

**Simple widgets (verbatim):** `gauge(score,label,statusTxt,c0,c1,max)`, `spark(data,w,h,c0,c1,opts)`,
`barChart(data,labels,hi,c0,c1,unit)`, `ringChart(items,...)`, `donut(segs)`, `calendarHeat(vals)`.

**Clinical figures (port + parameterize with an `opts` arg):**
`figControl, figBaseline, figRegression, figIMU, figDriver, figStacked, figPoincare, figPSD, figScalogram`.
Each currently generates its own seeded data with a hardcoded `rng(seed)` and fixed `title/xlabel`.
Change each signature to `figX(opts)` where `opts = {seed, title, xlabel, ylabel, band, note, calloutText}`
(all optional, falling back to the RA defaults). Replace `pAxis()` with a default xlabel of
`'Post-op day'`. Passing a per-metric `seed` makes every metric's figure look distinct but on-house.

**Dispatcher:** `Charts.render(name, opts)` → returns the SVG string for `Charts[name](opts)`; if
`name` is unknown, return a small neutral placeholder SVG (never throw). Also expose each fig fn
directly on `Charts`. Every function returns an **SVG string** sized to fit `.fig` (charts fill
their container width). Nothing in this file touches the DOM or the network.

**Ortho figure → metric mapping (informational; see §4 for per-metric `fig`):**
figControl→M1,M18 · figRegression→M2,M4 · figIMU→M3,M5,M6 · figBaseline→M7,M17 ·
figDriver→M12,M13 · figStacked→M9 · figPoincare/figPSD/figScalogram→M10,M11 (Clinical tier) ·
spark/barChart/gauge/calendarHeat→trend minis, adherence, risk gauges, wear-time coverage (M16).

---

## 3. Cross-surface event bus — `assets/bus.js`

A tiny pub/sub over `window.postMessage(msg,'*')` that works standalone AND across iframes
(and over `file://`). Global `Bus`:
- `Bus.on(type, handler)` — subscribe.
- `Bus.emit(type, payload)` — dispatch locally, post to `window.parent` (if framed), and post to
  every `<iframe>` in this document. Envelope: `{__mpbus:true, type, payload}`. On receiving a
  message with `__mpbus`, dispatch locally (do not re-broadcast — prevent loops).
- Event types: `TASK_ASSIGN` (payload: patientId), `RISK_ESCALATE` (patientId), `SMS_FIRE`
  ({patient,text}), `CHECKIN` ({patientId,symptom}), `NAV` ({surface,patientId}), `DEMO_STEP` ({n}).
Never read `event.origin` strictly (file:// is opaque). Guard against dispatching non-`__mpbus` msgs.

---

## 4. Data — `assets/data.js`

Load order in every page: `charts.js`, `data.js`, `bus.js`, `ui.js`, then page script.
Expose a global `MP` namespace. No storage APIs; pure in-memory seeded data.

```js
MP.RISK = {
 high:   {label:'High',        dot:'🔴', cls:'high',    bucket:'review'},
 medium: {label:'Needs attention', dot:'🟡', cls:'med', bucket:'drifting'},
 missing:{label:'Missing data', dot:'⚪', cls:'missing', bucket:'missing'},
 low:    {label:'Stable',      dot:'🟢', cls:'low',     bucket:'stable'},
};
MP.BUCKETS = [ {id:'review',name:'Needs review'},{id:'drifting',name:'Drifting'},
               {id:'missing',name:'Missing data'},{id:'stable',name:'Stable'} ];
MP.PROFILES = [ /* 6, see §4b */ ];
MP.METRIC_LIBRARY = { /* M1..M18 name/family/template/feasibility/tierMin/defaultFig, see §4c */ };
MP.PATIENTS = [ /* 9 patients, PRE-SORTED in triage order, see §4d */ ];
MP.getPatient = id => MP.PATIENTS.find(p=>p.id===id);
```

### 4a. Patient object shape
```js
{
 id:'marcus', name:'Marcus Reyes', initials:'MR', age:63, sex:'M',
 procedure:'Total Knee Replacement (TKA)', procShort:'TKA', useCase:'UC1',
 postopDay:8, surgeon:'Dr. Alvarez',
 risk:'high',                 // key into MP.RISK
 whyFlagged:'Pain ↑ 4 days · new fever · temp + HR coupled',
 dataConf:'high',             // 'high'|'med'|'low'  (this is M16)
 lastCheckin:'Today · 8:12a',
 summary:'…3–5 line quick appointment summary… (Monitoring signals for clinician review — not a diagnosis.)',
 hero:true,
 tasks:[ {title, why, verifiedBy, done:false, ring:0.0} … ],   // hero = the 5 from §5f
 metrics:[ metricInstance … ],                                  // report objects, see §4e
 conversation:[ {who:'copilot'|'patient', text} … ],
 adherence:{ rate:0.78, verified:11, assigned:14, selfAttested:2, days:[…14 of 0..1…], note:'…' },
 trajectory:{ state:'behind', pct:-18, note:'Behind the expected TKA curve since day 6.' }
}
```

### 4b. The 6 orthopedic profiles (`MP.PROFILES`)
```
1 prehab      "Prehab Readiness"          pre-op baseline
2 reserve     "Functional Reserve Drift"  slow decline
3 load        "Load Tolerance"            safe-load envelope  (M1,M2,M18)
4 independence"Home Independence Readiness" stairs/walking/transfers (M6,M8,M7)
5 labctx      "Lab–Wearable Ortho Context"  labs explain unstable recovery (M10,M11,M13)
6 exception   "Clinic Exception Queue"    triage: stable/drifting/missing/needs-review (M12,M16,M17)
```
Each: `{id,name,blurb,metricIds:[…]}`. On the dashboard, group the patient's metric report
objects under these six profile cards. Metrics not central to a profile can appear under the
most relevant one; every rendered metric belongs to exactly one profile card.

### 4c. The 18 metrics (`MP.METRIC_LIBRARY`) — id → {name, family, template, feasibility, tierMin, defaultFig}
Use the names/wording from `reference/MedPull-Ortho-Metrics-and-Task-Library.docx` (already read):
- M1 Acute:Chronic Load Ratio · Load&tolerance · T-A · Derived · tier1 · figControl
- M2 Load–Pain Sensitivity slope · Load&tolerance · T-D · Derived+App · tier1 · figRegression
- M3 Loading Asymmetry decay curve · Load&tolerance · T-B+T-A · Derived/Raw · tier2 · figIMU
- M4 Walking Economy · Functional capacity · T-D · Derived · tier2 · figRegression
- M5 Endurance-Fade Index · Functional capacity · T-B+T-C · Derived/Raw · tier2 · figIMU
- M6 Sit-to-Stand freq & rise tempo · Functional capacity · T-B · Raw · tier1 · barChart
- M7 Cadence Recovery curve · Functional capacity · T-A · Derived · tier1 · figBaseline
- M8 Stair reintroduction & flight tolerance · Functional capacity · T-A+T-B · Derived/Raw · tier1 · barChart
- M9 Nocturnal Disruption pain-proxy · Recovery quality · T-B+T-A+T-D · Derived+App · tier2 · figStacked
- M10 Autonomic Recovery trend · Recovery quality · T-A+T-D · Derived · tier2 · figPoincare
- M11 Circadian Rest-Activity amplitude · Recovery quality · T-C+T-A · Derived · tier3 · figPSD
- M12 Multi-Signal Deterioration Index · Complication surveillance · T-D · Derived · tier2 · figDriver  (GUARDED)
- M13 Thermal–Cardiac coupling · Complication surveillance · T-D · Derived · tier2 · figStacked  (GUARDED)
- M14 Verified-Adherence rate · Adherence · T-A · App+Derived · tier1 · calendarHeat
- M15 Disengagement-Risk score · Adherence · T-A · App · tier1 · gauge
- M16 Data-Confidence / coverage index · Adherence · T-A · Derived · tier1 · gauge  (gates all others)
- M17 Recovery-Trajectory fit & expected-curve deviation · Trajectory · T-D · Derived+App · tier1 · figBaseline
- M18 Plateau / regression change-point alert · Trajectory · T-A · Derived+App · tier1 · figControl

### 4d. Roster (`MP.PATIENTS`, PRE-SORTED: high → medium → missing → low)
| id | name | procedure | day | risk | whyFlagged | dataConf |
|----|------|-----------|-----|------|-----------|----------|
| marcus | Marcus Reyes (HERO) | Total Knee (TKA) | 8 | high | Pain ↑ 4d · new fever · temp+HR coupled | high |
| linda | Linda Park | Rotator cuff | 10 | medium | Night-pain proxy ↑ (sleep fragmented) | med |
| robert | Robert Ellison | Lumbar decompression | 6 | medium | Sitting tolerance low · walking improving | med |
| sofia | Sofia Marchetti | Ankle fracture (WB progression) | 21 | medium | Loading asymmetry high (expected) · DVT watch | med |
| aisha | Aisha Okonkwo | Total Hip (THA) | 15 | medium | Walking symmetry plateaued · steps ↓ | med |
| priya | Priya Nair | Total Hip (THA) | 9 | missing | Low watch wear · confidence low | low |
| david | David Chen | ACL reconstruction | 34 | low | On track · running reintroduced | high |
| james | James Whitfield | Total Knee (TKA) | 40 | low | Recovery ahead of curve | high |
| elena | Elena Vasquez | Meniscus repair | 19 | low | On track | high |

Every non-hero patient still gets a coherent (lighter) record: a 2–3 line `summary`, 3–6 metric
instances appropriate to their procedure/story, a short `conversation`, `adherence`, `trajectory`,
and 3–5 `tasks` from the relevant use case (UC1 TKA, UC2 THA, UC3 ACL, UC4 shoulder, UC5 lumbar,
UC6 fracture). Use a per-patient seed so series are stable.

### 4e. metricInstance (a "report object") shape
```js
{
 id:'M12', name:'Multi-Signal Deterioration Index', profileId:'exception', tier:2,
 status:'flag',                       // 'flag'(red) | 'watch'(amber) | 'ok'(green) | 'nodata'(grey)
 statusText:'Deviating from baseline',// short qualitative status vs personal baseline
 finding:'RHR, respiratory rate and wrist temperature are moving away from Marcus\'s own stable baseline together over the last two nights.',
 fig:'figDriver', figOpts:{seed:47, title:'Driver correlation matrix'},
 confidence:'high',                   // high|med|low  → confchip
 coverage:'6 nights of watch wear',   // derived-estimate provenance
 derived:true,                        // show "derived estimate" chip
 nextStep:'Recommend nurse outreach and wound review today.',
 guarded:true                         // M12/M13: MUST read "signals deviating from baseline — recommend review", never "detected/diagnosis"
}
```

---

## 5. GUARDRAILS (NON-NEGOTIABLE — bake into UI copy)
1. **Monitoring banner** on EVERY clinician analytics view (tablet detail + dashboard main):
   "Monitoring signals for clinician review — not a diagnosis."
2. **Be vague/qualitative on raw measurement.** Wearable-derived values are **directional trends,
   deltas, qualitative bands with a confidence/coverage tag** — NOT precise clinical numbers.
   e.g. "Steps trending down ~40% vs personal baseline · Med confidence" — NOT "3,412 steps".
   Every wearable metric carries a **"derived estimate"** + confidence chip.
3. **Patient side shows ZERO numbers/stats/graphs.** Qualitative encouragement only. No charts,
   no scores, no percentages, no counts on `patient.html`. (Task completion is a checkmark or a
   non-numeric progress ring — never "3/5".)
4. **M12 & M13 phrasing:** always "signals deviating from baseline — recommend review," NEVER
   "detected", "diagnosis", "PE/infection detected", "triage verdict", "return-to-play".
5. **High-risk → SMS-to-clinician:** when a patient crosses into High, fire a mock SMS-to-clinician
   toast/animation ("⚠️ High-risk change — Marcus Reyes").

### 5f. HERO — Marcus Reyes, TKA day 8, HIGH — full record
- **Check-in (patient words):** pain rising 4 days, worse in evenings; a new "feverish" feeling;
  knee feels warmer and more swollen; sleeping poorly.
- **Wearable-derived (qualitative + trend series):** steps trending down ~40% vs baseline; a load
  spike on day 6 then a crash; walking asymmetry plateaued elevated; sleep fragmentation up;
  resting HR drifting up; wrist temperature up; HRV suppressed.
- **Metric flags to render (report objects):** M1 (spike→drop, figControl), M2 (pain-cost slope
  steepening, figRegression), M3 (asymmetry plateau, figIMU), M7 (cadence, figBaseline),
  M9 (nocturnal disruption ↑, figStacked), M10 (HRV/RHR suppressed, figPoincare),
  **M12 (deterioration index elevated, figDriver — GUARDED)**, **M13 (thermal-cardiac coupling,
  figStacked — GUARDED)**, M14 (adherence, calendarHeat), M16 (data-confidence gauge),
  M17 (behind expected curve, figBaseline), M18 (change-point at day 6, figControl).
- **Quick appointment summary (VERBATIM):**
  > Day 8 TKA. Recovery has deviated from the expected trajectory since day 6. Reported pain
  > rising over 4 days with a new fever sensation and a warmer, more swollen knee. Overnight
  > temperature and resting heart rate have risen together with suppressed HRV and worsening
  > sleep — a coupled pattern that, with the reported fever and local warmth, warrants review for
  > possible surgical-site infection. Steps and walking symmetry have plateaued. **Recommended:**
  > nurse outreach today, assess the wound, consider bringing the follow-up forward.
  > (Monitoring signals for clinician review — not a diagnosis.)
- **The 5 tasks the clinician assigns Marcus** (appear on his phone Tasks page):
  1. Wear your watch overnight — *So we can keep an eye on your recovery while you rest.*
     (verified by: overnight data present)
  2. Log your knee pain, morning & evening — *A quick tap AM and PM helps us track how you feel.*
     (app entry)
  3. Take 3 short walks spread across the day — *Gentle, frequent movement helps you heal.*
     (step-bout clustering)
  4. Take a photo of your incision — *Brought forward to today so your team can check it.*
     (camera → review)
  5. Stay within today's gentle activity range — *Let's take it easy so you don't overdo it.*
     (step band)

---

## 6. Shared UI renderers — `assets/ui.js` (global `UI`)
All return HTML strings unless noted. Depend on `MP`, `Charts`.
- `UI.riskPill(risk)` → `<span class="rpill high">🔴 High</span>` (risk ∈ high/medium/missing/low).
- `UI.confChip(level, provenance)` → derived-estimate + confidence chip: `◔ derived estimate · Med confidence`.
- `UI.monitoringBanner()` → the `.mon-banner` "Monitoring signals for clinician review — not a diagnosis."
- `UI.quickSummary(patient)` → `.quick-sum` pinned block (left accent bar in risk color + summary text + Recommended line).
- `UI.reportObject(metric)` → full report-object card: header (name + tier badge + status pill),
  finding sentence, evidence graph via `Charts.render(metric.fig, metric.figOpts)`, `confChip`,
  next-step line. GUARDED metrics render the "signals deviating from baseline — recommend review" note.
- `UI.profileCard(profile, patient)` → a profile section containing its metrics' report objects.
- `UI.triageRow(patient)` → a `.triage-row` (name / procedure+"Day N" / risk pill / whyFlagged /
  confChip / lastCheckin); adds `.high` styling for high risk; `data-pid` attribute for click wiring.
- `UI.trajectoryBlock(patient)` → M17 recovery-trajectory-vs-expected block (figBaseline + CI note + on-track/behind/ahead).
- `UI.driverBlock(patient)` → M12/M13 deterioration index + driver matrix (figDriver/figStacked), GUARDED copy.
- `UI.adherenceBlock(patient)` → M14 verified adherence (calendarHeat + verified-vs-self-attested, RTM-friendly).
- `UI.rtmBlock(patient)` → RTM documentation summary (device-days, monitored days, interactions — auditable).
- `UI.conversationBlock(patient)` → chat history (copilot/patient bubbles).
- `UI.tierController(rootEl)` → wire a `.segment` Everyday/Advanced/Clinical toggle to set
  `body.tier-1/2/3` (reveals `.gate.t2/.t3`, hides `.t1only`) exactly like the RA file.
- `UI.showToast(html,{glass:true,timeout})` → floating `.toast` (used for the SMS-to-clinician demo).
- `UI.taskCard(task)` → patient task card: title + friendly "why" + qualitative completion (checkmark
  that can auto-tick, or a non-numeric ring). NO numbers.

---

## 7. Surfaces

### 7.1 patient.html — iPhone (392×850)
RA `.device/.screenframe/.island/.statusbar` shell. Home = **voice check-in chatbot**: a large
animated listening orb / mic (`.chat-orb`), a big talk button (`.talkbtn`); "listening" animates;
uses **SpeechSynthesis (TTS)** to speak the copilot's lines; renders the exchange as `.bubble` chat.
The scripted conversation: runs the daily check-in; gives a QUALITATIVE recovery summary ("You've
been putting in the work — let's take it a little easier today," never numbers); lets the patient
report a symptom (logs it; if it pushes to High, `Bus.emit('RISK_ESCALATE')` + `Bus.emit('SMS_FIRE')`);
explains how to reach the clinician and lets them request/schedule an earlier visit.
Bottom = **liquid-glass segmented slider** `.seg-slider` **Chat ⟷ Tasks** (glass thumb).
Tasks page = the 5 assigned tasks as `UI.taskCard` (qualitative completion only, gentle streak ok).
Listens for `TASK_ASSIGN` → populate/animate the 5 tasks in. **ZERO numbers/stats/graphs anywhere.**
Gentle disclaimer where natural ("I'm here to help you recover — for anything urgent, contact your care team.").

### 7.2 tablet.html — iPad "on the go"
`.device.tablet` frame. **Triage list** of all roster patients in sort order; each row = `UI.triageRow`
with risk pill + `whyFlagged` + conf chip (M16) + last check-in; High rows loudest. **Tap a patient →**
detail: `UI.monitoringBanner()` + `UI.quickSummary(patient)` PINNED AT TOP, then full data below
(report objects, profile cards, graphs) scrollable. **Quick-actions bar:** Message patient · Assign task ·
Escalate/Nurse triage · Bring follow-up forward. "Assign task" → `Bus.emit('TASK_ASSIGN',{patientId})`
(pushes Marcus's 5 tasks to the phone). Listens for `RISK_ESCALATE` → re-sort Marcus to top + flash.

### 7.3 dashboard.html — full-screen web app
`.dash` layout. **Left rail = Clinic Exception Queue** bucketed Needs review / Drifting / Missing data /
Stable with per-bucket counts at top (`UI.triageRow` within each). **Main = full per-patient record**
on select: `UI.monitoringBanner()` + `UI.quickSummary()` at top, then EVERYTHING grouped by the 6
profiles (`UI.profileCard`), each metric a report object with a reused-toolkit graph + conf chip +
next-step. Include `UI.trajectoryBlock` (M17), `UI.driverBlock` (M12/M13), `UI.conversationBlock`,
`UI.adherenceBlock` (M14), `UI.rtmBlock`. **Tier toggle (Everyday/Advanced/Clinical)** via
`UI.tierController` — Clinical reveals Poincaré/PSD/scalogram panels + method provenance + limitations.
Default-select Marcus.

### 7.4 index.html — guided demo shell (the star)
Full-screen stage presenting the three device mockups (embed `patient.html`, `tablet.html`,
`dashboard.html` as `<iframe>`s styled as devices) with an absolute **boxes-and-arrows** SVG overlay
(`.callout-svg`) + numbered `.callout-bubble` hints + pulsing `.hotspot`s. A clickable, self-narrating
story; each step can **speak** its key line via TTS. **prev / next / restart / autoplay** controls.
Drive the surfaces via `Bus.emit(...)` (postMessage into the iframes).

**The 6-beat narrative (dialogue must match the hero's data):**
1. **Patient (phone):** Marcus opens the app, taps the mic, check-in — reports pain rising for days,
   a new feverish feeling, a warmer/more swollen knee, poor sleep. Copilot responds empathetically (spoken), logs it.
2. **Risk escalation:** the check-in + trend push Marcus to **High** → fire the **SMS-to-clinician**
   toast ("⚠️ High-risk change — Marcus Reyes"); he jumps to the top of the triage list.
3. **Clinician (tablet, on the go):** opens Marcus, reads the quick summary, glances at graphs —
   pain ↑, steps ↓, sleep fragmented, load spike→drop, temp+HR coupled / deterioration index up, trajectory behind curve.
4. **Action:** clinician assigns the 5 tasks, messages Marcus, brings the follow-up forward / escalates to nurse triage.
5. **Back to patient (phone):** the 5 tasks appear on his Tasks page; a gentle nudge; copilot confirms the earlier appointment.
6. **"At the appointment" (dashboard):** full dashboard with the appointment summary pinned on top —
   "this is what your surgeon sees when you walk in."

---

## 8. Acceptance = BUILD_PROMPT §6 (see PLAN.md checklist). No console errors; no broken layout/overflow
at target viewports (dashboard ~1440×900, tablet ~1024×768, phone 390×844, shell ~1440×900);
guardrails visible; patient surface has zero numbers; demo narrative runs end-to-end with working
cross-surface task push + SMS toast + TTS + boxes-and-arrows. Runs fully from local files, no network.
