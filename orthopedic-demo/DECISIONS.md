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

## Round 5 — real neural voices (pre-generated, still zero-backend)
- **Every scripted line is now a real neural-TTS clip.** `tools/generate_voices.py` extracts all spoken
  lines (29 narrator lines in `index.html`, 8 check-in beats in `patient.html`), synthesizes them locally
  with **Kokoro-82M** (Apache-2.0, runs offline via `kokoro-onnx`; ~340 MB of model files, ~1.5 MB of MP3s
  out), and writes `assets/audio/manifest.js` keyed `"<role>|<exact text>"` with per-clip durations.
  Voices: narrator `af_heart`, assistant `af_bella`, Marcus `am_michael` — change them in `VOICES` and re-run.
- **`assets/voice.js` plays clips first, speechSynthesis second.** Clips queue back-to-back (mirroring
  the utterance queue callers rely on); `Voice.cancel()` flushes; any text without a clip falls back to
  the old Web Speech path, so ad-hoc lines still speak. New `Voice.duration(text, role)` lets
  `patient.html` time beats to the real clip length instead of a chars-per-ms estimate.
- The "just open index.html" constraint holds: clips are plain `<audio>` MP3s loaded relatively, so
  file:// still works and nothing leaves the machine at demo time. `tools/generate_voices.py --check`
  fails if a scripted line has no clip (run it after editing any `speak:`/beat text).
- Gotcha found on the way: espeak-ng (Kokoro's phonemizer) truncates its data path at ~160 chars and
  hard-exits, so the pip-bundled `espeakng-loader` breaks inside deeply nested venvs — the generator
  prefers Homebrew's `libespeak-ng` when present.

## Round 6 — formal narration + no overlapping voices
- **Narration rewritten in a formal clinical register** (same conciseness): e.g. "Meet Recovery
  Copilot. The patient just talks…" → "Recovery Copilot converts routine patient check-ins into
  monitored clinical signals…". Titles/bodies updated to match; copilot dialogue formalized
  ("It's time for your daily check-in"), Marcus's replies kept natural. Clips regenerated.
- **Narrator and kiosk voices no longer overlap.** With speechSynthesis, parent + iframe utterances
  were serialized by the OS speech engine; real `<audio>` clips play concurrently, so the shell now
  sequences explicitly: (1) speech-producing patient cmds (`start/answer/fever/sooner`) are deferred
  until the narrator clip ends (`Voice.duration` + 350 ms); (2) every `enterStep` emits a `patient hush`
  cmd so an in-flight kiosk line stops when the step changes; (3) `playThrough` emits `BEATS {ms}` on
  the Bus and the shell stretches the autoplay timer to narration + beats, so autoplay never advances
  mid-conversation (fixed dwells remain the floor/fallback).

## Round 7 — conversation-first phone steps, dashboard-first structure, tablet fixes
- **Kiosk conversation now plays FIRST on phone steps; the narrator comments after.** Sequencing
  inverted from Round 6: the speaky patient cmd fires immediately, the shell holds its narration in
  `narrTimer` (900 ms fallback), and the `BEATS {ms}` report re-times it to conversation-end + 400 ms.
  Autoplay stretches to beats + narration. The "In his own words…" narrator line was removed outright
  (the card text stays); every autoplay dwell is floored at narration + 800 ms so no clip is ever cut.
- **Demo restructured: the admin web dashboard is shown in full first; the tablet is a single
  "Tablet-ready" compatibility step near the end** (same console, so no duplicated tour). The tablet
  section's unique beats moved to the dashboard: guardrail (`hlBanner`), direct actions (new
  `hlActions` cmd highlighting `.dash-actions`), and assign-plan (new `assign` cmd clicking the real
  `[data-act=assign]` button → TASK_ASSIGN → phone).
- **Tablet notifications now render INSIDE the tablet screen.** The shell crops the tablet page's outer
  40 px, which hid the default `position:fixed; top:18px` toasts — tablet.html pre-creates
  `.toast-wrap` inside `.screenframe` (UI.showToast reuses it) positioned absolute below the status bar.
- **Tablet record summary no longer floats on scroll** — `.det-pin` is `position:static`, matching the
  Round-4 dashboard change.
- Verified end-to-end: two-window sequencing harness reports zero cross-window audio overlaps, and a
  28-keypress headless-browser walk through all 26 steps produced zero console errors/exceptions.
