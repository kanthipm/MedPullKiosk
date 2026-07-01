/* =============================================================================
   assets/data.js — MedPull Recovery Copilot (Orthopedic RTM demo)
   Seeded, in-memory mock data. No storage, no network. See SPEC.md §4.

   Exposes a global `MP` namespace:
     MP.RISK            risk-level lookup (high / medium / missing / low)
     MP.BUCKETS         exception-queue buckets (review / drifting / missing / stable)
     MP.PROFILES        the 6 orthopedic profile cards (§4b)
     MP.METRIC_LIBRARY  M1..M18 metric definitions (§4c)
     MP.PATIENTS        9 patients, PRE-SORTED high → medium → missing → low (§4d)
     MP.getPatient(id)  lookup by id

   Guardrails baked into the copy (SPEC §5): monitoring-not-diagnosis framing,
   wearable values kept qualitative (trends / deltas / bands + confidence tag,
   never precise clinical numbers), and M12/M13 always phrased as
   "signals deviating from baseline — recommend review" (never detected/diagnosis).
   Load order in every page: charts.js, data.js, bus.js, ui.js, then page script.
   ============================================================================= */
(function (global) {
  'use strict';

  var MP = {};

  /* ---- 4. Risk levels ---------------------------------------------------- */
  MP.RISK = {
    high:    { label: 'High',            dot: '🔴', cls: 'high',    bucket: 'review'   },
    medium:  { label: 'Needs attention', dot: '🟡', cls: 'med',     bucket: 'drifting' },
    missing: { label: 'Missing data',    dot: '⚪', cls: 'missing', bucket: 'missing'  },
    low:     { label: 'Stable',          dot: '🟢', cls: 'low',     bucket: 'stable'   }
  };

  /* ---- Exception-queue buckets ------------------------------------------- */
  MP.BUCKETS = [
    { id: 'review',   name: 'Needs review'  },
    { id: 'drifting', name: 'Drifting'      },
    { id: 'missing',  name: 'Missing data'  },
    { id: 'stable',   name: 'Stable'        }
  ];

  /* ---- 4b. The 6 orthopedic profiles ------------------------------------ *
   * Every rendered metric belongs to exactly one profile card. metricIds is
   * the canonical grouping; each metric instance also carries a profileId.   */
  MP.PROFILES = [
    { id: 'prehab',       name: 'Prehab Readiness',
      blurb: 'Pre-op baseline & engagement readiness — the reference every later signal is measured against.',
      metricIds: ['M14', 'M15'] },
    { id: 'reserve',      name: 'Functional Reserve Drift',
      blurb: 'Slow declines that erode capacity before they become symptoms — asymmetry, economy, endurance, nightly recovery.',
      metricIds: ['M3', 'M4', 'M5', 'M9'] },
    { id: 'load',         name: 'Load Tolerance',
      blurb: 'The personalized safe-load envelope — how much activity is helping versus overreaching.',
      metricIds: ['M1', 'M2', 'M18'] },
    { id: 'independence', name: 'Home Independence Readiness',
      blurb: 'The real-world function that gets a patient home — stairs, walking bouts and transfers.',
      metricIds: ['M6', 'M7', 'M8'] },
    { id: 'labctx',       name: 'Lab–Wearable Ortho Context',
      blurb: 'Where wearable-derived autonomic & thermal context helps explain an unstable recovery.',
      metricIds: ['M10', 'M11', 'M13'] },
    { id: 'exception',    name: 'Clinic Exception Queue',
      blurb: 'The triage view — stable, drifting, missing data, or needs-review — plus what gates our confidence.',
      metricIds: ['M12', 'M16', 'M17'] }
  ];

  /* ---- 4c. The 18 metrics ------------------------------------------------ *
   * id → { name, family, template, feasibility, tierMin, defaultFig, + extras
   * profileId / guarded / gates used by the shared renderers }.              */
  MP.METRIC_LIBRARY = {
    M1:  { name: 'Acute:Chronic Load Ratio',                        family: 'Load & tolerance',        template: 'T-A',           feasibility: 'Derived',        tierMin: 1, defaultFig: 'figControl',    profileId: 'load'         },
    M2:  { name: 'Load–Pain Sensitivity slope',                     family: 'Load & tolerance',        template: 'T-D',           feasibility: 'Derived + App',  tierMin: 1, defaultFig: 'figRegression', profileId: 'load'         },
    M3:  { name: 'Loading Asymmetry decay curve',                   family: 'Load & tolerance',        template: 'T-B + T-A',     feasibility: 'Derived / Raw',  tierMin: 2, defaultFig: 'figIMU',        profileId: 'reserve'      },
    M4:  { name: 'Walking Economy',                                 family: 'Functional capacity',     template: 'T-D',           feasibility: 'Derived',        tierMin: 2, defaultFig: 'figRegression', profileId: 'reserve'      },
    M5:  { name: 'Endurance-Fade Index',                            family: 'Functional capacity',     template: 'T-B + T-C',     feasibility: 'Derived / Raw',  tierMin: 2, defaultFig: 'figIMU',        profileId: 'reserve'      },
    M6:  { name: 'Sit-to-Stand frequency & rise tempo',            family: 'Functional capacity',     template: 'T-B',           feasibility: 'Raw',            tierMin: 1, defaultFig: 'barChart',      profileId: 'independence' },
    M7:  { name: 'Cadence Recovery curve',                          family: 'Functional capacity',     template: 'T-A',           feasibility: 'Derived',        tierMin: 1, defaultFig: 'figBaseline',   profileId: 'independence' },
    M8:  { name: 'Stair reintroduction & flight tolerance',        family: 'Functional capacity',     template: 'T-A + T-B',     feasibility: 'Derived / Raw',  tierMin: 1, defaultFig: 'barChart',      profileId: 'independence' },
    M9:  { name: 'Nocturnal Disruption pain-proxy',                family: 'Recovery quality',        template: 'T-B + T-A + T-D', feasibility: 'Derived + App', tierMin: 2, defaultFig: 'figStacked',    profileId: 'reserve'      },
    M10: { name: 'Autonomic Recovery trend',                        family: 'Recovery quality',        template: 'T-A + T-D',     feasibility: 'Derived',        tierMin: 2, defaultFig: 'figPoincare',   profileId: 'labctx'       },
    M11: { name: 'Circadian Rest-Activity amplitude',              family: 'Recovery quality',        template: 'T-C + T-A',     feasibility: 'Derived',        tierMin: 3, defaultFig: 'figPSD',        profileId: 'labctx'       },
    M12: { name: 'Multi-Signal Deterioration Index',               family: 'Complication surveillance', template: 'T-D',         feasibility: 'Derived',        tierMin: 2, defaultFig: 'figDriver',     profileId: 'exception', guarded: true },
    M13: { name: 'Thermal–Cardiac coupling',                       family: 'Complication surveillance', template: 'T-D',         feasibility: 'Derived',        tierMin: 2, defaultFig: 'figStacked',    profileId: 'labctx',    guarded: true },
    M14: { name: 'Verified-Adherence rate',                         family: 'Adherence',               template: 'T-A',           feasibility: 'App + Derived',  tierMin: 1, defaultFig: 'calendarHeat',  profileId: 'prehab'       },
    M15: { name: 'Disengagement-Risk score',                        family: 'Adherence',               template: 'T-A',           feasibility: 'App',            tierMin: 1, defaultFig: 'gauge',         profileId: 'prehab'       },
    M16: { name: 'Data-Confidence / coverage index',              family: 'Adherence',               template: 'T-A',           feasibility: 'Derived',        tierMin: 1, defaultFig: 'gauge',         profileId: 'exception', gates: true },
    M17: { name: 'Recovery-Trajectory fit & expected-curve deviation', family: 'Trajectory',         template: 'T-D',           feasibility: 'Derived + App',  tierMin: 1, defaultFig: 'figBaseline',   profileId: 'exception'    },
    M18: { name: 'Plateau / regression change-point alert',       family: 'Trajectory',              template: 'T-A',           feasibility: 'Derived + App',  tierMin: 1, defaultFig: 'figControl',    profileId: 'load'         }
  };

  /* ---- metric-instance builder ------------------------------------------ *
   * Merges a metric's library defaults (name / profileId / tier / fig /
   * guarded) with the per-patient report-object fields. `over` MUST provide
   * figOpts with a UNIQUE seed so every figure looks distinct but on-house.  */
  function metric(id, over) {
    var lib = MP.METRIC_LIBRARY[id];
    var inst = {
      id: id,
      name: lib.name,
      profileId: lib.profileId,
      tier: lib.tierMin,
      status: 'ok',
      statusText: '',
      finding: '',
      fig: lib.defaultFig,
      figOpts: {},
      confidence: 'med',
      coverage: '',
      derived: true,
      nextStep: ''
    };
    if (lib.guarded) inst.guarded = true;
    for (var k in over) if (Object.prototype.hasOwnProperty.call(over, k)) inst[k] = over[k];
    return inst;
  }

  /* ======================================================================= *
   * 4d/5f. HERO — Marcus Reyes · TKA day 8 · HIGH · full record
   * ======================================================================= */
  var marcus = {
    id: 'marcus', name: 'Marcus Reyes', initials: 'MR', age: 63, sex: 'M',
    procedure: 'Total Knee Replacement (TKA)', procShort: 'TKA', useCase: 'UC1',
    postopDay: 8, surgeon: 'Dr. Alvarez',
    risk: 'high',
    whyFlagged: 'Pain ↑ 4 days · new fever · temp + HR coupled',
    dataConf: 'high',
    lastCheckin: 'Today · 8:12a',
    seed: 8801,
    hero: true,
    summary:
      'Day 8 TKA. Recovery has deviated from the expected trajectory since day 6. ' +
      'Reported pain rising over 4 days with a new fever sensation and a warmer, more swollen knee. ' +
      'Overnight temperature and resting heart rate have risen together with suppressed HRV and worsening sleep — ' +
      'a coupled pattern that, with the reported fever and local warmth, warrants review for possible surgical-site infection. ' +
      'Steps and walking symmetry have plateaued. ' +
      'Recommended: nurse outreach today, assess the wound, consider bringing the follow-up forward. ' +
      '(Monitoring signals for clinician review — not a diagnosis.)',

    /* The 5 tasks the clinician assigns Marcus (SPEC §5f) */
    tasks: [
      { title: 'Wear your watch overnight',
        why: 'So we can keep an eye on your recovery while you rest.',
        verifiedBy: 'overnight data present', done: false, ring: 0.0 },
      { title: 'Log your knee pain, morning & evening',
        why: 'A quick tap AM and PM helps us track how you feel.',
        verifiedBy: 'app entry', done: false, ring: 0.0 },
      { title: 'Take 3 short walks spread across the day',
        why: 'Gentle, frequent movement helps you heal.',
        verifiedBy: 'step-bout clustering', done: false, ring: 0.0 },
      { title: 'Take a photo of your incision',
        why: 'Brought forward to today so your team can check it.',
        verifiedBy: 'camera → review', done: false, ring: 0.0 },
      { title: "Stay within today's gentle activity range",
        why: "Let's take it easy so you don't overdo it.",
        verifiedBy: 'step band', done: false, ring: 0.0 }
    ],

    /* ~12 metric report objects (M1,M2,M3,M7,M9,M10,M12,M13,M14,M16,M17,M18) */
    metrics: [
      metric('M1', {
        status: 'flag', statusText: 'Spike then drop vs baseline',
        finding: "Activity load spiked above Marcus's personal ceiling on day 6, then dropped sharply below his usual floor — an over-then-under pattern rather than steady progression.",
        figOpts: { seed: 11, title: 'Load control chart', ylabel: 'Load (σ vs baseline)', band: 'baseline ±1σ', calloutText: 'Day 6 spike' },
        confidence: 'high', coverage: '7 days of step & cadence data', derived: true,
        nextStep: 'Reinforce a steady, capped activity range; avoid another spike.'
      }),
      metric('M2', {
        status: 'flag', statusText: 'Pain cost per unit load steepening',
        finding: 'Reported pain per unit of activity has climbed for four days — the same amount of walking is now costing Marcus noticeably more pain than a week ago.',
        figOpts: { seed: 12, title: 'Load → next-day pain', xlabel: 'Relative activity load', ylabel: 'Reported pain' },
        confidence: 'med', coverage: 'AM/PM pain logs + step load', derived: true,
        nextStep: 'Review the load prescription; keep progression pain-limited.'
      }),
      metric('M3', {
        status: 'watch', statusText: 'Asymmetry plateaued elevated',
        finding: 'Weight-bearing asymmetry between the operated and healthy leg has stopped improving and is holding elevated — the expected week-2 symmetry gain has stalled.',
        figOpts: { seed: 13, title: 'Loading asymmetry decay', ylabel: 'Asymmetry index', band: 'expected decay' },
        confidence: 'med', coverage: 'gait bouts, 5 of 7 days', derived: true,
        nextStep: 'Encourage symmetric weight-shift drills; recheck in 3 days.'
      }),
      metric('M7', {
        status: 'watch', statusText: 'Cadence below expected band',
        finding: 'Preferred walking cadence is tracking under the expected recovery ribbon for day 8 and has flattened over the last two days.',
        figOpts: { seed: 17, title: 'Cadence recovery vs baseline', ylabel: 'Cadence (rel.)', band: 'expected ribbon' },
        confidence: 'med', coverage: 'walking bouts, 6 of 7 days', derived: true,
        nextStep: 'Short, frequent walks to re-establish cadence.'
      }),
      metric('M9', {
        status: 'flag', statusText: 'Overnight restlessness ↑ with evening pain',
        finding: 'Overnight movement and awakenings have risen in step with higher evening pain — nights have become increasingly fragmented over the past four days.',
        figOpts: { seed: 19, title: 'Nocturnal disruption vs evening pain', note: 'Fragmentation rising' },
        confidence: 'med', coverage: '6 nights of watch wear', derived: true,
        nextStep: 'Evening pain-control review; sleep-position guidance.'
      }),
      metric('M10', {
        status: 'flag', statusText: 'HRV suppressed, RHR drifting up',
        finding: "Overnight heart-rate variability has stayed suppressed while resting heart rate drifts above Marcus's own baseline — an autonomic-recovery pattern moving the wrong way.",
        figOpts: { seed: 23, title: 'Autonomic recovery · Poincaré', note: 'HRV suppressed' },
        confidence: 'high', coverage: '6 nights of watch wear', derived: true,
        nextStep: 'Factor into the infection-watch review below.'
      }),
      metric('M12', {
        status: 'flag', statusText: 'Signals deviating from baseline — recommend review',
        finding: "Resting heart rate, respiratory rate and wrist temperature are moving away from Marcus's own stable baseline together over the last two nights — a multi-signal deviation rather than any single reading.",
        figOpts: { seed: 47, title: 'Driver correlation matrix' },
        confidence: 'high', coverage: '6 nights of multi-sensor wear', derived: true, guarded: true,
        nextStep: 'Signals deviating from baseline — recommend review: nurse outreach and wound check today.'
      }),
      metric('M13', {
        status: 'flag', statusText: 'Signals deviating from baseline — recommend review',
        finding: 'Wrist temperature and resting heart rate are rising together while HRV drops — a coupled overnight pattern. With the reported fever and local warmth, this is a signal to review rather than a conclusion.',
        figOpts: { seed: 53, title: 'Thermal–cardiac coupling', note: 'Temp + HR coupled' },
        confidence: 'high', coverage: '6 nights of watch wear', derived: true, guarded: true,
        nextStep: 'Signals deviating from baseline — recommend review for possible surgical-site infection.'
      }),
      metric('M14', {
        status: 'watch', statusText: 'Verified completion slipping this week',
        finding: 'Marcus has completed most assigned tasks, but device-verified completion has slipped over the last three days as his symptoms worsened.',
        figOpts: { seed: 14, title: 'Verified adherence' },
        confidence: 'high', coverage: 'app + device confirmation', derived: false,
        nextStep: 'Lighten the load and re-motivate through the check-in.'
      }),
      metric('M16', {
        status: 'ok', statusText: 'Good coverage — high confidence',
        finding: 'Watch wear and sync have been consistent this week, so the signals above are well-supported.',
        figOpts: { seed: 16, title: 'Data confidence / coverage', score: 86, label: 'Coverage', statusTxt: 'High confidence' },
        confidence: 'high', coverage: 'strong wear-time & fresh sync', derived: true,
        nextStep: 'Confidence high — the signals above are reliable.'
      }),
      metric('M17', {
        status: 'flag', statusText: 'Behind the expected curve since day 6',
        finding: "Marcus's recovery has tracked below the expected TKA trajectory band since day 6 — roughly 18% behind the modeled curve for his procedure and age.",
        figOpts: { seed: 71, title: 'Recovery trajectory vs expected', ylabel: 'Function (rel.)', band: 'expected CI', note: 'Deviating since day 6' },
        confidence: 'high', coverage: 'derived from the trajectory model', derived: true,
        nextStep: 'Bring the follow-up forward; reassess the plan.'
      }),
      metric('M18', {
        status: 'flag', statusText: 'Change-point around day 6',
        finding: 'An automated change-point sits at day 6, where steady early progress broke into a decline across several signals at once.',
        figOpts: { seed: 73, title: 'Change-point detection', ylabel: 'Composite (σ)', band: 'stable window', calloutText: 'Day 6 change-point' },
        confidence: 'high', coverage: 'multi-signal composite trend', derived: true,
        nextStep: 'Treat day 6 as the inflection; investigate the cause.'
      })
    ],

    /* 8-turn check-in matching his data */
    conversation: [
      { who: 'copilot', text: 'Morning, Marcus. How are you feeling today compared with yesterday?' },
      { who: 'patient', text: "Honestly, not great. My knee's been hurting more and more for about four days now, worse in the evenings." },
      { who: 'copilot', text: "I'm sorry to hear that. Has anything else changed — how does the knee itself feel?" },
      { who: 'patient', text: 'It feels warm and more swollen than before. And last night I felt kind of feverish and could barely sleep.' },
      { who: 'copilot', text: "Thank you for telling me — that's really helpful. A warm, swollen knee with a feverish feeling is something your care team will want to look at." },
      { who: 'patient', text: 'Is that something I should be worried about?' },
      { who: 'copilot', text: "I've logged everything and flagged it for your care team to review today. Let's also get a photo of your incision so they can see it. For anything urgent, please contact your care team directly." },
      { who: 'copilot', text: "I've asked them about bringing your follow-up forward — I'll confirm the new time right here as soon as it's set." }
    ],

    adherence: {
      rate: 0.78, verified: 11, assigned: 14, selfAttested: 2,
      days: [1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0],
      note: '11 of 14 assigned tasks device-verified; 2 self-attested. Verified completion slipped over the last three days as symptoms worsened.'
    },

    trajectory: { state: 'behind', pct: -18, note: 'Behind the expected TKA curve since day 6.' }
  };

  /* ======================================================================= *
   * Non-hero patients — lighter but coherent records
   * ======================================================================= */

  /* Linda Park · Rotator cuff (UC4 shoulder) · day 10 · medium */
  var linda = {
    id: 'linda', name: 'Linda Park', initials: 'LP', age: 58, sex: 'F',
    procedure: 'Rotator Cuff Repair', procShort: 'RC repair', useCase: 'UC4',
    postopDay: 10, surgeon: 'Dr. Nguyen',
    risk: 'medium', whyFlagged: 'Night-pain proxy ↑ (sleep fragmented)', dataConf: 'med',
    lastCheckin: 'Today · 7:40a', seed: 8802,
    summary:
      'Day 10 rotator-cuff repair. Daytime shoulder function is progressing, but overnight rest has become more fragmented ' +
      'with higher reported night pain this week. Worth a check on evening pain control and sleep positioning. ' +
      '(Monitoring signals for clinician review — not a diagnosis.)',
    tasks: [
      { title: 'Do your pendulum exercises twice today', why: 'Gentle swinging keeps the shoulder moving without strain.', verifiedBy: 'motion bout', done: false, ring: 0.0 },
      { title: 'Log your shoulder pain before bed', why: 'Helps us see how your nights are really going.', verifiedBy: 'app entry', done: false, ring: 0.0 },
      { title: "Wear your sling when you're up and about", why: 'Protects the repair while it heals.', verifiedBy: 'reminder ack', done: false, ring: 0.0 },
      { title: 'Ice the shoulder after exercises', why: 'Keeps swelling and soreness down.', verifiedBy: 'app entry', done: false, ring: 0.0 }
    ],
    metrics: [
      metric('M9', {
        status: 'flag', statusText: 'Nights more fragmented, pain ↑',
        finding: 'Overnight restlessness has risen alongside higher pre-bed pain — nights are more broken this week than last.',
        figOpts: { seed: 101, title: 'Nocturnal disruption vs evening pain' },
        confidence: 'med', coverage: '5 nights of watch wear', derived: true,
        nextStep: 'Evening pain control + sleep-position guidance.'
      }),
      metric('M3', {
        status: 'watch', statusText: 'Shoulder ROM asymmetry slow to close',
        finding: 'Movement symmetry between the operated and healthy arm is improving slowly and remains below the expected week-2 mark.',
        figOpts: { seed: 102, title: 'Loading / ROM asymmetry', ylabel: 'Asymmetry index' },
        confidence: 'med', coverage: 'motion bouts, 5 of 7 days', derived: true,
        nextStep: 'Continue guided pendulum + passive ROM.'
      }),
      metric('M14', {
        status: 'ok', statusText: 'Adherence holding well',
        finding: 'Linda is completing her exercises consistently, with only the occasional self-reported day.',
        figOpts: { seed: 103, title: 'Verified adherence' },
        confidence: 'high', coverage: 'app + device confirmation', derived: false,
        nextStep: 'Keep up the routine.'
      }),
      metric('M16', {
        status: 'watch', statusText: 'Coverage adequate — medium confidence',
        finding: 'Watch wear is reasonable but skips some nights, so overnight signals carry medium confidence.',
        figOpts: { seed: 104, title: 'Data confidence / coverage', score: 68, label: 'Coverage', statusTxt: 'Medium confidence' },
        confidence: 'med', coverage: 'moderate wear-time, occasional gaps', derived: true,
        nextStep: 'Encourage nightly wear to sharpen the sleep read.'
      }),
      metric('M17', {
        status: 'watch', statusText: 'Slightly behind on sleep-quality recovery',
        finding: 'Daytime function tracks the expected curve; overnight recovery quality is trailing the band.',
        figOpts: { seed: 105, title: 'Recovery trajectory vs expected', band: 'expected CI' },
        confidence: 'med', coverage: 'derived from the trajectory model', derived: true,
        nextStep: 'Recheck once night pain settles.'
      })
    ],
    conversation: [
      { who: 'copilot', text: 'Hi Linda, how did you sleep last night?' },
      { who: 'patient', text: 'Not well — the shoulder aches whenever I roll over, so I keep waking up.' },
      { who: 'copilot', text: "That sounds frustrating. I've noted the night pain — let's try icing before bed and keeping the sling on overnight. I'll share this with your team." },
      { who: 'patient', text: 'Thanks, that would really help.' }
    ],
    adherence: {
      rate: 0.82, verified: 9, assigned: 11, selfAttested: 1,
      days: [1, 1, 1, 0.5, 1, 1, 1, 1, 0.5, 1, 1, 1, 0, 1],
      note: '9 of 11 assigned tasks device-verified; 1 self-attested. Consistent daytime, softer overnight coverage.'
    },
    trajectory: { state: 'behind', pct: -8, note: 'Daytime function on track; overnight recovery quality slightly behind.' }
  };

  /* Robert Ellison · Lumbar decompression (UC5) · day 6 · medium */
  var robert = {
    id: 'robert', name: 'Robert Ellison', initials: 'RE', age: 67, sex: 'M',
    procedure: 'Lumbar Decompression', procShort: 'Lumbar decomp', useCase: 'UC5',
    postopDay: 6, surgeon: 'Dr. Bello',
    risk: 'medium', whyFlagged: 'Sitting tolerance low · walking improving', dataConf: 'med',
    lastCheckin: 'Today · 9:05a', seed: 8803,
    summary:
      'Day 6 lumbar decompression. Walking tolerance is improving nicely, but sitting tolerance remains low and is limiting daily activity. ' +
      'No red-flag neuro signals reported. ' +
      '(Monitoring signals for clinician review — not a diagnosis.)',
    tasks: [
      { title: 'Take short, frequent walks', why: 'Walking is the best medicine for your back right now.', verifiedBy: 'step-bout clustering', done: false, ring: 0.0 },
      { title: 'Avoid sitting for long stretches', why: 'Stand and move every 30 minutes to stay comfortable.', verifiedBy: 'reminder ack', done: false, ring: 0.0 },
      { title: 'Log any leg numbness or tingling', why: 'Tell us right away if anything changes down your leg.', verifiedBy: 'app entry', done: false, ring: 0.0 },
      { title: 'Do your gentle nerve-glide exercises', why: 'Keeps the nerve moving freely as you heal.', verifiedBy: 'motion bout', done: false, ring: 0.0 }
    ],
    metrics: [
      metric('M6', {
        status: 'watch', statusText: 'Transfer / sitting tolerance low',
        finding: 'Sit-to-stand transitions are slower and less frequent than expected — sitting comfort is the main limiter right now.',
        figOpts: { seed: 111, title: 'Sit-to-stand tempo & frequency' },
        confidence: 'med', coverage: 'transfer bouts, 6 of 7 days', derived: true,
        nextStep: 'Frequent position changes; short sitting bouts.'
      }),
      metric('M7', {
        status: 'ok', statusText: 'Walking cadence improving',
        finding: 'Preferred cadence is climbing back toward baseline — the walking side of recovery is going well.',
        figOpts: { seed: 112, title: 'Cadence recovery vs baseline', band: 'expected ribbon' },
        confidence: 'med', coverage: 'walking bouts, 6 of 7 days', derived: true,
        nextStep: 'Keep building walk duration gradually.'
      }),
      metric('M17', {
        status: 'watch', statusText: 'Mixed — walking ahead, sitting behind',
        finding: 'Overall trajectory sits just under the expected band, driven by sitting tolerance rather than mobility.',
        figOpts: { seed: 113, title: 'Recovery trajectory vs expected', band: 'expected CI' },
        confidence: 'med', coverage: 'derived from the trajectory model', derived: true,
        nextStep: 'Reassess sitting tolerance in a few days.'
      }),
      metric('M16', {
        status: 'watch', statusText: 'Coverage adequate — medium confidence',
        finding: 'Wear-time is solid on active days with a couple of quieter days, giving medium confidence.',
        figOpts: { seed: 114, title: 'Data confidence / coverage', score: 66, label: 'Coverage', statusTxt: 'Medium confidence' },
        confidence: 'med', coverage: 'moderate wear-time', derived: true,
        nextStep: 'Consistent daytime wear will tighten the read.'
      })
    ],
    conversation: [
      { who: 'copilot', text: "Morning Robert. How's the walking going today?" },
      { who: 'patient', text: 'Walking actually feels better — but sitting still kills me after just a few minutes.' },
      { who: 'copilot', text: "Good to hear the walking's improving. Let's keep sitting time short and stand up often. I've logged the sitting discomfort for your team." },
      { who: 'patient', text: 'Sounds good, thanks.' }
    ],
    adherence: {
      rate: 0.80, verified: 8, assigned: 10, selfAttested: 1,
      days: [1, 1, 0.5, 1, 1, 1, 1, 0.5, 1, 1, 0, 1, 1, 1],
      note: '8 of 10 assigned tasks device-verified; 1 self-attested. Good walking adherence.'
    },
    trajectory: { state: 'behind', pct: -5, note: 'Sitting tolerance behind expectation; walking ahead of it.' }
  };

  /* Sofia Marchetti · Ankle fracture, WB progression (UC6 fracture) · day 21 · medium */
  var sofia = {
    id: 'sofia', name: 'Sofia Marchetti', initials: 'SM', age: 45, sex: 'F',
    procedure: 'Ankle Fracture (weight-bearing progression)', procShort: 'Ankle Fx', useCase: 'UC6',
    postopDay: 21, surgeon: 'Dr. Kaur',
    risk: 'medium', whyFlagged: 'Loading asymmetry high (expected) · DVT watch', dataConf: 'med',
    lastCheckin: 'Yesterday · 6:22p', seed: 8804,
    summary:
      'Day 21 ankle fracture, progressing weight-bearing. Loading asymmetry remains high, which is expected at this stage. ' +
      'Flagged for routine DVT watch — no confirmed signs; monitoring ankle-pump adherence and calf comfort. ' +
      '(Monitoring signals for clinician review — not a diagnosis.)',
    tasks: [
      { title: 'Follow your weight-bearing limit today', why: 'Only put down as much weight as your team advised.', verifiedBy: 'load band', done: false, ring: 0.0 },
      { title: 'Do your ankle pumps and circles', why: 'Keeps blood moving and helps prevent clots.', verifiedBy: 'motion bout', done: false, ring: 0.0 },
      { title: 'Watch for calf swelling or pain', why: 'Tell us straight away if your calf feels tight or sore.', verifiedBy: 'app entry', done: false, ring: 0.0 },
      { title: 'Elevate your foot when resting', why: 'Helps swelling settle between activity.', verifiedBy: 'reminder ack', done: false, ring: 0.0 }
    ],
    metrics: [
      metric('M3', {
        status: 'watch', statusText: 'Asymmetry high (expected this stage)',
        finding: 'Loading remains weighted toward the healthy side — high asymmetry is expected while weight-bearing is being progressed.',
        figOpts: { seed: 121, title: 'Loading asymmetry decay', ylabel: 'Asymmetry index', band: 'expected decay' },
        confidence: 'med', coverage: 'gait bouts, 6 of 7 days', derived: true,
        nextStep: 'Continue graded weight-bearing per protocol.'
      }),
      metric('M1', {
        status: 'watch', statusText: 'Load progressing within envelope',
        finding: 'Daily load is rising steadily and staying inside the personalized safe band — no overreach so far.',
        figOpts: { seed: 122, title: 'Load control chart', band: 'baseline ±1σ' },
        confidence: 'med', coverage: '7 days of step & load data', derived: true,
        nextStep: 'Advance load per weight-bearing schedule.'
      }),
      metric('M12', {
        status: 'watch', statusText: 'Signals deviating from baseline — recommend review',
        finding: "Overnight resting heart rate shows a mild deviation from Sofia's baseline — worth keeping an eye on given the routine DVT-watch context.",
        figOpts: { seed: 123, title: 'Driver correlation matrix' },
        confidence: 'med', coverage: '5 nights of multi-sensor wear', derived: true, guarded: true,
        nextStep: 'Signals deviating from baseline — recommend review: confirm no calf swelling or pain.'
      }),
      metric('M16', {
        status: 'watch', statusText: 'Coverage adequate — medium confidence',
        finding: 'Wear-time is generally good with an occasional gap, giving medium confidence on the overnight signals.',
        figOpts: { seed: 124, title: 'Data confidence / coverage', score: 70, label: 'Coverage', statusTxt: 'Medium confidence' },
        confidence: 'med', coverage: 'moderate wear-time', derived: true,
        nextStep: 'Encourage nightly wear during the DVT-watch window.'
      }),
      metric('M17', {
        status: 'ok', statusText: 'Tracking the expected fracture curve',
        finding: 'Weight-bearing progression is roughly on the expected curve for this stage of ankle-fracture recovery.',
        figOpts: { seed: 125, title: 'Recovery trajectory vs expected', band: 'expected CI' },
        confidence: 'med', coverage: 'derived from the trajectory model', derived: true,
        nextStep: 'Continue the current progression.'
      })
    ],
    conversation: [
      { who: 'copilot', text: "Hi Sofia, how's the ankle feeling as you put more weight on it?" },
      { who: 'patient', text: "It's getting stronger, but I'm still favoring the other side a lot." },
      { who: 'copilot', text: "That's expected at this stage. Keep up the ankle pumps, and let me know right away if your calf ever feels tight or swollen." },
      { who: 'patient', text: 'Will do.' }
    ],
    adherence: {
      rate: 0.85, verified: 12, assigned: 14, selfAttested: 1,
      days: [1, 1, 1, 1, 1, 1, 0.5, 1, 1, 1, 1, 1, 0, 1],
      note: '12 of 14 assigned tasks device-verified; 1 self-attested. Strong ankle-pump adherence.'
    },
    trajectory: { state: 'on-track', pct: 2, note: 'Weight-bearing progression roughly on the expected fracture curve.' }
  };

  /* Aisha Okonkwo · Total Hip (THA, UC2) · day 15 · medium */
  var aisha = {
    id: 'aisha', name: 'Aisha Okonkwo', initials: 'AO', age: 71, sex: 'F',
    procedure: 'Total Hip Replacement (THA)', procShort: 'THA', useCase: 'UC2',
    postopDay: 15, surgeon: 'Dr. Alvarez',
    risk: 'medium', whyFlagged: 'Walking symmetry plateaued · steps ↓', dataConf: 'med',
    lastCheckin: 'Today · 8:50a', seed: 8805,
    summary:
      'Day 15 total hip replacement. Early gains have plateaued — walking symmetry and daily step volume have leveled off ' +
      'over the past several days rather than continuing to climb. Worth a progression check. ' +
      '(Monitoring signals for clinician review — not a diagnosis.)',
    tasks: [
      { title: 'Follow your hip precautions', why: 'Keep movements safe while the hip heals.', verifiedBy: 'reminder ack', done: false, ring: 0.0 },
      { title: 'Take your walks with the walker', why: 'Steady, supported steps rebuild your stride.', verifiedBy: 'step-bout clustering', done: false, ring: 0.0 },
      { title: 'Do your hip-strengthening exercises', why: 'Builds the muscles that steady your hip.', verifiedBy: 'motion bout', done: false, ring: 0.0 },
      { title: 'Log how far you walk each day', why: 'A quick note helps us see your progress.', verifiedBy: 'app entry', done: false, ring: 0.0 }
    ],
    metrics: [
      metric('M3', {
        status: 'watch', statusText: 'Walking symmetry plateaued',
        finding: 'Left/right symmetry has leveled off rather than continuing to improve through week 2.',
        figOpts: { seed: 131, title: 'Loading asymmetry decay', ylabel: 'Asymmetry index', band: 'expected decay' },
        confidence: 'med', coverage: 'gait bouts, 6 of 7 days', derived: true,
        nextStep: 'Progress gait drills; reassess symmetry.'
      }),
      metric('M7', {
        status: 'watch', statusText: 'Cadence flat below band',
        finding: 'Preferred cadence has flattened just under the expected recovery ribbon for day 15.',
        figOpts: { seed: 132, title: 'Cadence recovery vs baseline', band: 'expected ribbon' },
        confidence: 'med', coverage: 'walking bouts, 6 of 7 days', derived: true,
        nextStep: 'Add a second short walk to nudge cadence.'
      }),
      metric('M4', {
        status: 'watch', statusText: 'Walking economy not improving',
        finding: 'The heart-rate cost of walking is holding steady rather than easing — endurance gains have stalled with the plateau.',
        figOpts: { seed: 133, title: 'Walking economy · HR cost', xlabel: 'Gait output', ylabel: 'HR cost' },
        confidence: 'med', coverage: 'walking bouts, 5 of 7 days', derived: true,
        nextStep: 'Interval-style walk progression.'
      }),
      metric('M17', {
        status: 'watch', statusText: 'Plateaued below expected curve',
        finding: 'The recovery curve has flattened just under the expected band since roughly day 12.',
        figOpts: { seed: 134, title: 'Recovery trajectory vs expected', band: 'expected CI', note: 'Plateau ~day 12' },
        confidence: 'med', coverage: 'derived from the trajectory model', derived: true,
        nextStep: 'Review the exercise progression with the patient.'
      }),
      metric('M16', {
        status: 'watch', statusText: 'Coverage adequate — medium confidence',
        finding: 'Wear-time is reasonable, supporting medium confidence in the plateau read.',
        figOpts: { seed: 135, title: 'Data confidence / coverage', score: 67, label: 'Coverage', statusTxt: 'Medium confidence' },
        confidence: 'med', coverage: 'moderate wear-time', derived: true,
        nextStep: 'Consistent wear will confirm the trend.'
      })
    ],
    conversation: [
      { who: 'copilot', text: 'Hi Aisha, how are your walks going this week?' },
      { who: 'patient', text: "I feel like I've stalled — I'm not walking any further than I was last week." },
      { who: 'copilot', text: "Thanks for noticing that. Your walking rhythm has leveled off, so let's adjust the plan. I've shared this with your team to review your exercises." },
      { who: 'patient', text: 'Okay, thank you.' }
    ],
    adherence: {
      rate: 0.79, verified: 10, assigned: 13, selfAttested: 2,
      days: [1, 1, 1, 0.5, 1, 1, 0, 1, 1, 0.5, 1, 1, 0, 1],
      note: '10 of 13 assigned tasks device-verified; 2 self-attested. A few missed walks this week.'
    },
    trajectory: { state: 'behind', pct: -10, note: 'Walking symmetry and volume plateaued around day 12.' }
  };

  /* Priya Nair · Total Hip (THA, UC2) · day 9 · MISSING data */
  var priya = {
    id: 'priya', name: 'Priya Nair', initials: 'PN', age: 52, sex: 'F',
    procedure: 'Total Hip Replacement (THA)', procShort: 'THA', useCase: 'UC2',
    postopDay: 9, surgeon: 'Dr. Alvarez',
    risk: 'missing', whyFlagged: 'Low watch wear · confidence low', dataConf: 'low',
    lastCheckin: '3 days ago', seed: 8806,
    summary:
      'Day 9 total hip replacement. Watch wear has been sparse this week, so recovery signals are currently low-confidence. ' +
      'Priya reports feeling okay; the priority is re-establishing device wear so monitoring can resume. ' +
      '(Monitoring signals for clinician review — not a diagnosis.)',
    tasks: [
      { title: 'Wear your watch through the day', why: "It helps us support your recovery — we've missed some of your data.", verifiedBy: 'wear-time present', done: false, ring: 0.0 },
      { title: 'Follow your hip precautions', why: 'Keep movements safe while the hip heals.', verifiedBy: 'reminder ack', done: false, ring: 0.0 },
      { title: 'Take your supported walks', why: 'Steady steps rebuild your stride.', verifiedBy: 'step-bout clustering', done: false, ring: 0.0 },
      { title: 'Charge and sync your watch tonight', why: 'A quick sync keeps your care team in the loop.', verifiedBy: 'sync fresh', done: false, ring: 0.0 }
    ],
    metrics: [
      metric('M16', {
        status: 'nodata', statusText: 'Low coverage — confidence low',
        finding: "Watch wear has been sparse and syncs are stale this week, so we can't reliably assess Priya's recovery signals right now.",
        figOpts: { seed: 141, title: 'Data confidence / coverage', score: 34, label: 'Coverage', statusTxt: 'Low confidence' },
        confidence: 'low', coverage: 'sparse wear-time, stale sync', derived: true,
        nextStep: 'Reconnect the watch and confirm wear; re-check once coverage improves.'
      }),
      metric('M14', {
        status: 'nodata', statusText: 'Not enough verified data',
        finding: 'Only a handful of tasks have device confirmation this week; most completion is self-reported.',
        figOpts: { seed: 142, title: 'Verified adherence' },
        confidence: 'low', coverage: 'mostly self-attested', derived: false,
        nextStep: 'Re-establish device wear to verify tasks.'
      }),
      metric('M17', {
        status: 'nodata', statusText: 'Insufficient data to place on curve',
        finding: "There isn't enough recent signal to place Priya reliably on the expected recovery curve.",
        figOpts: { seed: 143, title: 'Recovery trajectory vs expected', band: 'expected CI' },
        confidence: 'low', coverage: 'insufficient recent data', derived: true,
        nextStep: 'Revisit once wear-time recovers.'
      }),
      metric('M7', {
        status: 'nodata', statusText: 'Cadence read unavailable',
        finding: 'Too few walking bouts were captured this week to read cadence recovery.',
        figOpts: { seed: 144, title: 'Cadence recovery vs baseline', band: 'expected ribbon' },
        confidence: 'low', coverage: 'few walking bouts', derived: true,
        nextStep: 'Resume daytime wear to capture walks.'
      })
    ],
    conversation: [
      { who: 'copilot', text: "Hi Priya — I've noticed your watch hasn't synced in a few days. How have you been?" },
      { who: 'patient', text: "Oh, I keep forgetting to charge it. I've been feeling alright though." },
      { who: 'copilot', text: "No problem — glad you're doing okay. If you can wear and sync it, we'll be able to keep a better eye on your recovery." },
      { who: 'patient', text: "I'll charge it tonight." }
    ],
    adherence: {
      rate: 0.55, verified: 5, assigned: 11, selfAttested: 4,
      days: [1, 0, 0.5, 0, 1, 0, 0, 0.5, 1, 0, 0.5, 0, 1, 0],
      note: '5 of 11 assigned tasks device-verified; 4 self-attested. Sparse wear-time limits verification.'
    },
    trajectory: { state: 'unknown', pct: 0, note: 'Not enough recent data to place Priya on the recovery curve.' }
  };

  /* David Chen · ACL reconstruction (UC3) · day 34 · LOW */
  var david = {
    id: 'david', name: 'David Chen', initials: 'DC', age: 29, sex: 'M',
    procedure: 'ACL Reconstruction', procShort: 'ACL', useCase: 'UC3',
    postopDay: 34, surgeon: 'Dr. Rossi',
    risk: 'low', whyFlagged: 'On track · running reintroduced', dataConf: 'high',
    lastCheckin: 'Today · 7:15a', seed: 8807,
    summary:
      'Day 34 ACL reconstruction. Recovery is on track — limb symmetry and cadence have normalized and running has been ' +
      'reintroduced without swelling. Continue the graded return-to-sport plan. ' +
      '(Monitoring signals for clinician review — not a diagnosis.)',
    tasks: [
      { title: 'Keep up your strengthening program', why: 'Strong muscles protect your new ligament.', verifiedBy: 'motion bout', done: false, ring: 0.0 },
      { title: 'Log how your knee feels after running', why: 'Helps us guide your return to sport safely.', verifiedBy: 'app entry', done: false, ring: 0.0 },
      { title: 'Do your balance and agility drills', why: 'Rebuilds control for cutting and pivoting later.', verifiedBy: 'motion bout', done: false, ring: 0.0 },
      { title: 'Ease into running as planned', why: 'Build up gradually — there is no rush back to full speed.', verifiedBy: 'run-bout detection', done: false, ring: 0.0 }
    ],
    metrics: [
      metric('M3', {
        status: 'ok', statusText: 'Symmetry normalized',
        finding: 'Left/right loading is essentially even now — the operated leg is sharing load well.',
        figOpts: { seed: 151, title: 'Loading asymmetry decay', ylabel: 'Asymmetry index', band: 'expected decay' },
        confidence: 'high', coverage: 'gait & run bouts, 7 of 7 days', derived: true,
        nextStep: 'Continue graded return-to-sport.'
      }),
      metric('M7', {
        status: 'ok', statusText: 'Cadence recovered',
        finding: 'Preferred cadence is back within the healthy band and stable across the week.',
        figOpts: { seed: 152, title: 'Cadence recovery vs baseline', band: 'expected ribbon' },
        confidence: 'high', coverage: 'walking & run bouts', derived: true,
        nextStep: 'Maintain; progress running volume gradually.'
      }),
      metric('M17', {
        status: 'ok', statusText: 'On / ahead of expected curve',
        finding: 'Recovery tracks the top of the expected ACL band, consistent with an uncomplicated return to running.',
        figOpts: { seed: 153, title: 'Recovery trajectory vs expected', band: 'expected CI' },
        confidence: 'high', coverage: 'derived from the trajectory model', derived: true,
        nextStep: 'Stay on the graded plan.'
      }),
      metric('M16', {
        status: 'ok', statusText: 'Excellent coverage — high confidence',
        finding: 'Consistent wear and fresh syncs make every signal well-supported.',
        figOpts: { seed: 154, title: 'Data confidence / coverage', score: 91, label: 'Coverage', statusTxt: 'High confidence' },
        confidence: 'high', coverage: 'strong wear-time & fresh sync', derived: true,
        nextStep: 'No action — coverage excellent.'
      }),
      metric('M1', {
        status: 'ok', statusText: 'Load steady within envelope',
        finding: 'Training load is rising gradually and staying comfortably inside the safe band.',
        figOpts: { seed: 155, title: 'Load control chart', band: 'baseline ±1σ' },
        confidence: 'high', coverage: '7 days of load data', derived: true,
        nextStep: 'Progress load per the return-to-run plan.'
      })
    ],
    conversation: [
      { who: 'copilot', text: 'Hey David, how did the return to running feel?' },
      { who: 'patient', text: 'Really good — no swelling afterward and the knee felt stable the whole time.' },
      { who: 'copilot', text: "That's great progress. Keep building up gradually and logging how it feels each time." },
      { who: 'patient', text: 'Will do, thanks.' }
    ],
    adherence: {
      rate: 0.93, verified: 13, assigned: 14, selfAttested: 0,
      days: [1, 1, 1, 1, 1, 1, 1, 1, 0.5, 1, 1, 1, 1, 1],
      note: '13 of 14 assigned tasks device-verified. Excellent engagement.'
    },
    trajectory: { state: 'ahead', pct: 8, note: 'Ahead of the expected ACL curve; running reintroduced on schedule.' }
  };

  /* James Whitfield · Total Knee (TKA, UC1) · day 40 · LOW */
  var james = {
    id: 'james', name: 'James Whitfield', initials: 'JW', age: 68, sex: 'M',
    procedure: 'Total Knee Replacement (TKA)', procShort: 'TKA', useCase: 'UC1',
    postopDay: 40, surgeon: 'Dr. Alvarez',
    risk: 'low', whyFlagged: 'Recovery ahead of curve', dataConf: 'high',
    lastCheckin: 'Yesterday · 5:10p', seed: 8808,
    summary:
      'Day 40 total knee replacement. Recovery is ahead of the expected curve — strong walking cadence, good range of motion, ' +
      'and no pain flares reported. Now in the maintenance phase. ' +
      '(Monitoring signals for clinician review — not a diagnosis.)',
    tasks: [
      { title: 'Keep up your daily walks', why: 'Steady walking keeps building your strength.', verifiedBy: 'step-bout clustering', done: false, ring: 0.0 },
      { title: 'Do your knee-bending exercises', why: 'Maintains the range you have worked hard for.', verifiedBy: 'motion bout', done: false, ring: 0.0 },
      { title: 'Log your knee pain if it flares', why: 'Only when you need to — you are doing great.', verifiedBy: 'app entry', done: false, ring: 0.0 },
      { title: 'Ice after your exercises', why: 'Keeps any swelling down.', verifiedBy: 'app entry', done: false, ring: 0.0 }
    ],
    metrics: [
      metric('M17', {
        status: 'ok', statusText: 'Ahead of expected curve',
        finding: 'Recovery sits above the expected TKA band and continues to trend up — nicely ahead of schedule.',
        figOpts: { seed: 161, title: 'Recovery trajectory vs expected', band: 'expected CI' },
        confidence: 'high', coverage: 'derived from the trajectory model', derived: true,
        nextStep: 'Maintenance phase; continue current routine.'
      }),
      metric('M7', {
        status: 'ok', statusText: 'Cadence at / above baseline',
        finding: 'Preferred walking cadence is back to his pre-surgery baseline and steady.',
        figOpts: { seed: 162, title: 'Cadence recovery vs baseline', band: 'expected ribbon' },
        confidence: 'high', coverage: 'walking bouts, 7 of 7 days', derived: true,
        nextStep: 'Keep up the daily walks.'
      }),
      metric('M1', {
        status: 'ok', statusText: 'Load healthy within envelope',
        finding: 'Activity load is steady and well within the safe band — no overreach, no under-loading.',
        figOpts: { seed: 163, title: 'Load control chart', band: 'baseline ±1σ' },
        confidence: 'high', coverage: '7 days of load data', derived: true,
        nextStep: 'Continue as is.'
      }),
      metric('M16', {
        status: 'ok', statusText: 'Excellent coverage — high confidence',
        finding: 'Consistent wear and fresh syncs keep confidence high across the board.',
        figOpts: { seed: 164, title: 'Data confidence / coverage', score: 90, label: 'Coverage', statusTxt: 'High confidence' },
        confidence: 'high', coverage: 'strong wear-time & fresh sync', derived: true,
        nextStep: 'No action needed.'
      })
    ],
    conversation: [
      { who: 'copilot', text: "Morning James, how's the knee this week?" },
      { who: 'patient', text: "Feeling strong — I'm getting around the house with no trouble now." },
      { who: 'copilot', text: "Fantastic — you're ahead of schedule. Keep up the walks and the exercises." },
      { who: 'patient', text: 'Thanks!' }
    ],
    adherence: {
      rate: 0.90, verified: 12, assigned: 13, selfAttested: 1,
      days: [1, 1, 1, 1, 1, 0.5, 1, 1, 1, 1, 1, 1, 0, 1],
      note: '12 of 13 assigned tasks device-verified; 1 self-attested. Consistently engaged.'
    },
    trajectory: { state: 'ahead', pct: 12, note: 'Ahead of the expected TKA curve.' }
  };

  /* Elena Vasquez · Meniscus repair (UC3 knee) · day 19 · LOW */
  var elena = {
    id: 'elena', name: 'Elena Vasquez', initials: 'EV', age: 34, sex: 'F',
    procedure: 'Meniscus Repair', procShort: 'Meniscus', useCase: 'UC3',
    postopDay: 19, surgeon: 'Dr. Rossi',
    risk: 'low', whyFlagged: 'On track', dataConf: 'high',
    lastCheckin: 'Today · 8:30a', seed: 8809,
    summary:
      'Day 19 meniscus repair. Recovery is on track — steady weight-bearing progression, good quad activation, and only ' +
      'mild morning stiffness reported. ' +
      '(Monitoring signals for clinician review — not a diagnosis.)',
    tasks: [
      { title: 'Follow your weight-bearing guidance', why: 'Protects the repair as it knits together.', verifiedBy: 'load band', done: false, ring: 0.0 },
      { title: 'Do your quad-strengthening exercises', why: 'Keeps the knee stable and strong.', verifiedBy: 'motion bout', done: false, ring: 0.0 },
      { title: 'Take gentle daily walks', why: 'Steady movement helps the knee heal.', verifiedBy: 'step-bout clustering', done: false, ring: 0.0 },
      { title: 'Log any catching or locking', why: 'Tell us if the knee ever catches or locks.', verifiedBy: 'app entry', done: false, ring: 0.0 }
    ],
    metrics: [
      metric('M17', {
        status: 'ok', statusText: 'On the expected curve',
        finding: 'Recovery is tracking the expected meniscus-repair band closely and steadily.',
        figOpts: { seed: 171, title: 'Recovery trajectory vs expected', band: 'expected CI' },
        confidence: 'high', coverage: 'derived from the trajectory model', derived: true,
        nextStep: 'Continue the current plan.'
      }),
      metric('M7', {
        status: 'ok', statusText: 'Cadence within band',
        finding: 'Preferred cadence is inside the healthy ribbon; morning stiffness eases as the day goes on.',
        figOpts: { seed: 172, title: 'Cadence recovery vs baseline', band: 'expected ribbon' },
        confidence: 'high', coverage: 'walking bouts, 7 of 7 days', derived: true,
        nextStep: 'Gentle morning mobility to ease stiffness.'
      }),
      metric('M3', {
        status: 'ok', statusText: 'Asymmetry resolving',
        finding: 'Left/right loading is close to even, in line with a smooth meniscus-repair recovery.',
        figOpts: { seed: 173, title: 'Loading asymmetry decay', ylabel: 'Asymmetry index', band: 'expected decay' },
        confidence: 'high', coverage: 'gait bouts, 7 of 7 days', derived: true,
        nextStep: 'Continue quad work.'
      }),
      metric('M16', {
        status: 'ok', statusText: 'Strong coverage — high confidence',
        finding: 'Wear-time and syncs are consistent, so the on-track read is well-supported.',
        figOpts: { seed: 174, title: 'Data confidence / coverage', score: 88, label: 'Coverage', statusTxt: 'High confidence' },
        confidence: 'high', coverage: 'strong wear-time & fresh sync', derived: true,
        nextStep: 'No action needed.'
      })
    ],
    conversation: [
      { who: 'copilot', text: "Hi Elena, how's the knee feeling?" },
      { who: 'patient', text: 'Pretty good — a little stiff in the mornings, but it loosens up.' },
      { who: 'copilot', text: "That morning stiffness is normal and should keep easing. You're right on track." },
      { who: 'patient', text: 'Good to know, thanks.' }
    ],
    adherence: {
      rate: 0.88, verified: 11, assigned: 13, selfAttested: 1,
      days: [1, 1, 1, 1, 0.5, 1, 1, 1, 1, 1, 0, 1, 1, 1],
      note: '11 of 13 assigned tasks device-verified; 1 self-attested. Reliably engaged.'
    },
    trajectory: { state: 'on-track', pct: 1, note: 'Tracking the expected meniscus-repair curve.' }
  };

  /* ---- 4d. Roster — PRE-SORTED high → medium → missing → low ------------- */
  MP.PATIENTS = [
    marcus,                          // high
    linda, robert, sofia, aisha,     // medium
    priya,                           // missing data
    david, james, elena              // low
  ];

  MP.getPatient = function (id) {
    for (var i = 0; i < MP.PATIENTS.length; i++) {
      if (MP.PATIENTS[i].id === id) return MP.PATIENTS[i];
    }
    return null;
  };

  global.MP = MP;
})(typeof window !== 'undefined' ? window : this);
