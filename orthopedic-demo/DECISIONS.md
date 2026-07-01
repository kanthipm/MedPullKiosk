# DECISIONS.md — running log

Autonomous overnight build. Decisions made where the spec is silent.

## Foundational
- **Fresh start.** The prior `run_overnight.sh` loop produced nothing — all 40 iterations
  failed with `401 Invalid authentication credentials` (see `.build-logs/*`). Building from
  scratch off the reference material in `reference/`.
- **Reference is present.** `reference/micro_lab_my_body_phone_app.html` (the RA house style)
  and `reference/MedPull-Ortho-Metrics-and-Task-Library.docx` were read in full before coding.
- **Single source of truth = `SPEC.md`.** Every build agent conforms to it, so the four
  independently-built surfaces stay visually and structurally coherent.

## Tech
- **No build step, no CDNs, no framework.** Matches the RA file; maximizes "just open it".
- **Shell embeds surfaces via `<iframe>`** (isolation + each surface works standalone),
  driven by `postMessage('*')` so it works over `file://` (opaque origin) without CORS pain.
- **Charts ported verbatim** from the RA toolkit and parameterized with an `opts` arg so each
  ortho metric gets a distinct-but-house-style figure. No chart libraries.
- **Seeded RNG** (`rng(seed)`) per patient/metric so all mock series are stable across reloads.

## Product / content
- **Orthopedics only** (P1). P0/general and pediatric ideas ignored per spec.
- **Patient surface shows ZERO numbers/stats/graphs** — qualitative encouragement only.
- **Guardrail copy is verbatim from spec.** M12/M13 always read "signals deviating from
  baseline — recommend review," never "detected/diagnosis."
- **Wearable metrics are directional** (trend/delta/qualitative band) with a
  confidence + "derived estimate" chip — never precise clinical numbers.
- **Hero = Marcus Reyes, TKA day 8, HIGH** with the full record from spec §2e.
- **Roster sort order** on clinician surfaces: 🔴 High/needs-review → 🟡 needs-attention →
  ⚪ missing-data → 🟢 stable.

## Later decisions (appended during build)
- **Build executed as a dependency-ordered multi-agent workflow** (foundations → shared UI →
  surfaces → demo shell). All agents conformed to `SPEC.md`; the four surfaces compose the shared
  `ui.js`/`styles.css`, so they came out visually + structurally coherent.
- **Verification done in the main loop with Playwright/Chromium** (headless), over BOTH
  `http://localhost` and `file://`. Screenshots were viewed for every surface + several demo beats.
- **Results:** zero console errors on all four surfaces and all three iframes; patient chat/tasks
  contain **no health numbers** (a `softenNumbers()` helper even spells out stray digits in tasks);
  the patient symptom report fires `CHECKIN → RISK_ESCALATE → SMS_FIRE`; the demo shell's beat 2
  raises the SMS-to-clinician toast and beat 5 pushes the 5 tasks onto the phone; dashboard tier
  toggle reveals the Clinical (Poincaré/PSD/scalogram) panels; the full record renders 6 profile
  cards, 12 report objects, and 19 hand-rolled SVG figures with no layout breaks.
- **Patient check-in is a two-tap mic flow** (tap → assistant listens/responds → tap → recap →
  symptom chips). Voice lines are spoken via `speechSynthesis` when available (guarded for headless).

## Guided-demo rework (post-review feedback)
Feedback: callout boxes were mispositioned, some iframe text looked blurry, the tour was too short
(only 6 beats), and the bottom controls were cluttered. Changes:
- **Crispness:** the old shell downscaled each device with `transform: scale()`, which bitmap-blurs
  an iframe. Confirmed by a side-by-side test that CSS **`zoom`** re-rasterizes crisply while
  preserving the full desktop layout — so the shell now sizes the one active device with `zoom`.
- **Correct highlight boxes:** instead of the shell guessing normalized coordinates over the device
  (unreliable, and impossible to measure across `file://` opaque-origin iframes), **each surface now
  rings its OWN target element** (`.demo-hl`, layout-safe: outline + box-shadow only) driven by a new
  `DEMO` Bus command. The shell just shows a narration card + an arrow to the device. Boxes always land right.
- **One device at a time** (crisp, centered) instead of three scaled-down mockups with blurry thumbnails.
- **Detail:** expanded from 6 beats to a **~25-step walkthrough** covering every surface and workflow —
  patient (voice check-in, recap, symptom→escalation→SMS, earlier-visit, glass Chat/Tasks slider,
  5 tasks); tablet (triage board, row anatomy, open, banner, quick-actions, assign→push, profiles);
  dashboard (exception queue, missing-data/Priya M16 case, pinned summary, report-object anatomy,
  trajectory M17, complication surveillance M12/M13, Everyday/Advanced/Clinical tiers, adherence/RTM,
  conversation, the loop).
- **Controls:** removed the step counter, step dots, and Prev/Next. Bottom bar is now **Restart +
  Autoplay** only; advance by clicking the device / hotspot or pressing → (a numberless progress bar
  shows position). Verified over both http:// and file:// with zero console errors; autoplay auto-advances.

## Guided-demo rework, round 2 (wide-screen layout + intro)
Feedback: on a wide external display the callout was "so far off" — a giant arrow across empty space.
Root cause: the shell used a full-width `1fr | card` grid, so on wide screens the device stayed small
on the left while the card was pinned to the far right. My earlier verification only used 1440×900,
where it happened to look fine. Fixes + lesson:
- **Centered cluster layout.** The active device and its narration card are now positioned by JS as a
  single **centered cluster** (`device | gap | card`), so it looks balanced at any width. The arrow is
  always a short hop from the card to the device. Verified at **1280, 1440, 1920, and 2560** wide.
- **Verification lesson:** always screenshot at multiple viewport widths (including ultrawide), not just 1440.
- **Backstory intro.** Added two opening "cover" cards that hype/explain the product before the
  walkthrough ("Recovery doesn't stop at discharge." → "Meet Recovery Copilot.").
- **Pagehint leak.** At high `zoom` the phone/tablet standalone `.pagehint` strip peeked above the
  bezel. `bus.js` now adds `html.framed` when a surface is embedded (`window.top !== window.self`,
  readable cross-origin) and `styles.css` hides `.pagehint` when framed. Standalone surfaces unaffected.
- **Visible SMS alert.** The shell now listens for `SMS_FIRE` and shows its own toast, so the
  secure-alert confirmation is visible while the phone (not the clinician surface) is on screen.
- **Arrow draw-length** bug (dash truncated long paths) fixed by raising the dash length; arrows are short now anyway.

## RA's two Claude artifacts — incorporated (source pasted by user)
Cloudflare blocked automated access, so the user pasted both React source files. Takeaways:
- **Patient app (`MyBody`)**: it *does* show numbers (steps, bpm, recovery score). The user reaffirmed
  "no insights for the patient," so our patient app stays as-is (zero numbers) — already more aligned
  with the guardrail than their own artifact. **No patient-side changes.**
- **Clinician console (`MICRO-LAB`)**: used as inspiration for dashboard presentation. Adapted its
  patterns into our **light** house style (not a dark rebuild, and still vanilla JS — theirs is
  React + recharts, which our no-build constraint forbids):
  - **charts.js** — six new light SVG figures: `figLoadBand` (acute:chronic load band w/ floor+ceiling),
    `figSymmetry` (gait-symmetry recovery curve), `figScatter` (cadence↔HR decoupling), `figHist`
    (walking-bout distribution), `figCircadian` (cosinor), `figComposite` (multivariate T² / Mahalanobis).
  - **dashboard.html** — new sections: a **composite-deviation KPI strip** (index σ · trajectory ·
    data-confidence M16 · verified-adherence M14); a **deep-analytics lens switcher** (8 lenses over one
    stream — the console's signature interaction); a **Movement & Biomechanics** ortho grid (M1/M3/M4/M5/M6
    + multivariate surveillance with driver-contribution bars); and a **Sensor scope / feasibility filter**
    listing excluded profiles (raw PPG, EDA, continuous ECG, body composition) — the console's honesty touch.
  - **index.html** — three new guided-demo steps showcase the KPI headline, the analytics lenses, and the
    biomechanics panel (new `data-demo` anchors: index / analytics / biomechanics). Tour is now 31 steps.
  - Clinician-side derived analytics (composite index in σ, trajectory %, adherence %) are permitted by the
    guardrails (derived estimates with confidence tags); M12/M13 keep the "signals deviating — recommend
    review" phrasing. Verified over http:// and file:// with zero console errors; lens switcher interactive.

## Round 4 — kiosk patient UI, bigger devices, better TTS, simpler text
- **Patient phone → MedPull-Kiosk style.** Replaced the chat-bubble / quick-reply-chips / Chat-Tasks
  slider with a **one-question-at-a-time voice kiosk** (matching `MedPullKiosk/GuidedIntakeScreen`):
  a thin top progress line, a centered speaker label + big line, an **animated waveform** reacting to
  the mic, and **Marcus's answer streaming in as a coloured transcript** — no option chips. The copilot
  speaks (female) and Marcus speaks (male). Standalone: tap the mic to advance; in the demo the shell
  drives it via the same `DEMO` commands (reset/start/answer/fever/sooner/tasks). Escalation + the 5
  qualitative tasks are preserved; still **zero numbers**.
- **Better + faster TTS** (`assets/voice.js`, shared): the only no-backend option is the Web Speech API
  (CapCut/neural TTS needs a cloud API + build step, which "just open index.html" forbids). It now picks
  the **best available natural voice** — a **female** for the narrator + assistant, a **male** for Marcus
  (prefers Google/Samantha/Ava … and Google-Male/Daniel/Tom …) — and speaks **faster** (rate ~1.06–1.14).
  Chrome exposes higher-quality Google voices than Safari; installing a macOS "Enhanced" voice improves it further.
- **Instruction text simpler / shorter / bigger.** Every narration `body` cut to one short line; card
  title 21→26px, body 14→18px, intro 32→40px.
- **Devices as big as possible, dynamically.** Compact narration card (≤340px) + smaller margins + trimmed
  header/controls + zoom cap raised to 2.0, so the phone/tablet/dashboard fill the stage and shrink/grow
  with the window. Verified 1280 → 2560.
- **Dashboard summary no longer sticky** — `.quick-sum` is `position:static`, so it stays at the top of
  the record instead of floating on scroll. (Only the compact topbar remains pinned.)
- All verified over http:// and file:// with zero console errors; SMS escalation + task-push still fire.
