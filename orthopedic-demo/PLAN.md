# PLAN.md — MedPull Recovery Copilot (Orthopedic) demo

## Architecture
Static, front-end-only, no build step, no external CDNs. Vanilla HTML/CSS/JS.
Openable by double-clicking `index.html`; deployable to Vercel as a static site.

```
index.html            # guided demo shell (landing) — embeds the 3 surfaces via iframes
patient.html          # patient phone app (iPhone mockup) — voice check-in + tasks
tablet.html           # clinician tablet (iPad mockup) — triage list + patient detail
dashboard.html        # clinician admin dashboard (full-screen) — full per-patient record
assets/styles.css     # design tokens (from RA :root) + components + liquid-glass
assets/charts.js      # ported RA SVG figure toolkit + ortho metric renderers (no libs)
assets/data.js        # seeded mock roster (9 patients) + hero (Marcus) full record
assets/ui.js          # shared renderers: report-object, profile card, triage row, chips, banner, toast, tier toggle, glass slider, task card
assets/bus.js         # postMessage-based cross-surface pub/sub event bus
SPEC.md               # authoritative contract for all of the above (source of truth)
DECISIONS.md          # running log of decisions & assumptions
DEMO_COMPLETE.md      # sentinel (written last) — click-path for presenting the demo
```

## Cross-surface communication
`assets/bus.js` wraps `window.postMessage(msg,'*')`. The demo shell (parent) and each
iframe (child) both `Bus.on(type, fn)` / `Bus.emit(type, payload)`. Events:
`TASK_ASSIGN`, `RISK_ESCALATE`, `SMS_FIRE`, `NAV`, `CHECKIN`, `DEMO_STEP`.
Works standalone (in-page) and across iframes. `postMessage('*')` avoids file:// origin issues.

## Charts
Port the RA hand-rolled SVG toolkit verbatim (`rng/series/esc/uid/grad/pol/arc/cgeo/cframe/
cborder/cpath/gss/VIR/vir/svgWrap/callout/gauge/spark/barChart/ringChart/donut/calendarHeat`
plus `figControl/figBaseline/figRegression/figIMU/figDriver/figStacked/figPoincare/figPSD/
figScalogram`). Each `fig*` accepts an optional `opts` `{seed,title,xlabel,ylabel,band,...}`
so different ortho metrics render distinct-but-consistent figures. All return SVG strings.

## Build order (dependency-ordered)
1. Foundations (parallel): `styles.css`, `charts.js`, `data.js`, `bus.js`.
2. Shared UI: `ui.js` (depends on the four above).
3. Surfaces (parallel): `dashboard.html`, `tablet.html`, `patient.html`.
4. Demo shell: `index.html` (embeds the three).
5. Verify with headless browser (Playwright): screenshot each surface at target viewport,
   view the images, check console errors, fix layout/overflow/dead buttons. Iterate.
6. Write `DEMO_COMPLETE.md`.

## Definition-of-Done checklist (mirrors BUILD_PROMPT §6)
- [ ] index.html opens, no console errors, presents guided demo shell.
- [ ] All 4 surfaces render, no broken layout/overflow at target viewports (screenshots viewed).
- [ ] Patient: voice check-in speaks via TTS, chat bubbles, report-symptom + request-appt; ZERO numbers/stats/graphs; liquid-glass Chat⟷Tasks slider; Tasks page shows the 5 hero tasks (qualitative only).
- [ ] Tablet: triaged list in correct order; tap → detail with quick summary pinned top + full data; quick-action bar wired.
- [ ] Dashboard: exception-queue rail + full per-patient record, all metrics as report objects grouped by the 6 profiles, each with a reused-toolkit graph + confidence chip + next step; tier toggle; trajectory-vs-expected, deterioration/driver, conversation, adherence, RTM summary.
- [ ] Guided demo: 6-beat narrative via clickable hotspots + boxes-and-arrows; cross-surface task push; high-risk SMS toast; coherent dialogue matching hero data; prev/next/restart.
- [ ] Guardrails visible: "monitoring, not diagnosis" banner; qualitative trends + confidence/"derived estimate" chips (not precise measurements); M12/M13 phrased as "signals deviating from baseline — recommend review."
- [ ] Visual quality matches RA aesthetic + liquid-glass; polished; screenshots viewed and approved.
- [ ] No backend/network; runs from local files; valid static Vercel deploy.
- [ ] PLAN.md, DECISIONS.md, DEMO_COMPLETE.md exist; DEMO_COMPLETE documents the click-path.
