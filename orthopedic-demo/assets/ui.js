/* =============================================================================
   assets/ui.js — MedPull Recovery Copilot (Orthopedic RTM demo)
   Shared UI renderers (SPEC.md §6). Global `UI`.

   Every member returns an HTML STRING except UI.tierController / UI.showToast,
   which act on the DOM. Depends on window.MP (data.js) and window.Charts
   (charts.js); window.Bus is used opportunistically if present. No frameworks,
   no CDNs, no build step, no storage/network. Works from file://.

   Load order in every page: charts.js, data.js, bus.js, ui.js, then page script.

   Guardrails baked into copy (SPEC §5): a "Monitoring signals for clinician
   review — not a diagnosis." banner is available for every clinician view; every
   wearable metric carries a derived-estimate + confidence chip; M12/M13 (guarded)
   always render "signals deviating from baseline — recommend review", never
   "detected/diagnosis". All user-facing text is HTML-escaped.
============================================================================= */
(function (global) {
  'use strict';

  var C = global.Charts || {};
  /* HTML escape — reuse the charts toolkit's escaper, fall back if absent. */
  var esc = (typeof C.esc === 'function') ? C.esc : function (s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (m) {
      return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' })[m];
    });
  };

  var MONITORING = 'Monitoring signals for clinician review — not a diagnosis.';
  var GUARD_NOTE = 'Signals deviating from baseline — recommend review. Not a diagnosis.';

  /* -------------------------------------------------------------- helpers */

  /* Confidence level → CSS class token (high|med|low). Accepts 'medium'. */
  function confLevel(l) {
    l = String(l == null ? '' : l).toLowerCase();
    if (l === 'medium') return 'med';
    if (l === 'high' || l === 'med' || l === 'low') return l;
    return 'med';
  }
  function confLabel(l) { return { high: 'High', med: 'Med', low: 'Low' }[confLevel(l)]; }

  /* Risk key → MP.RISK record (defensive fallback to a neutral 'low'). */
  function riskRec(risk) {
    var R = (global.MP && global.MP.RISK) || {};
    return R[risk] || R.low || { label: 'Stable', dot: '🟢', cls: 'low', bucket: 'stable' };
  }

  /* Report-object status → short pill word + gauge gradient. */
  var STATUS_WORD = { flag: 'Flag', watch: 'Watch', ok: 'On track', nodata: 'No data' };
  function statusColors(status) {
    return ({
      flag:   ['#ff8fa3', '#ff4d6d'],
      watch:  ['#ffd166', '#e07b00'],
      ok:     ['#9bf06a', '#0a9d57'],
      nodata: ['#c9d2e3', '#7c879e']
    })[status] || ['#56ccf2', '#2f80ed'];
  }

  /* Tier → badge (reuses .badge-tier / .badge-tier.prem / .badge-tier.clin). */
  function tierBadge(tier) {
    if (tier >= 3) return '<span class="badge-tier clin">Clinical</span>';
    if (tier === 2) return '<span class="badge-tier prem">Advanced</span>';
    return '<span class="badge-tier" style="background:#eef2fb;color:#5b647d">Everyday</span>';
  }

  function findMetric(patient, id) {
    var a = (patient && patient.metrics) || [];
    for (var i = 0; i < a.length; i++) if (a[i].id === id) return a[i];
    return null;
  }

  /* Deterministic seeded 0..1 series for widget figures that carry no data. */
  function seededHeat(seed, n) {
    var r = C.rng ? C.rng(seed || 9) : Math.random, v = [], i;
    n = n || 14;
    for (i = 0; i < n; i++) v.push(+(0.34 + r() * 0.64).toFixed(2));
    return v;
  }
  function seededBars(seed) {
    var r = C.rng ? C.rng(seed || 9) : Math.random, data = [], i, hi = 0;
    for (i = 0; i < 7; i++) { data.push(Math.round(5 + r() * 11)); if (data[i] > data[hi]) hi = i; }
    return { data: data, labels: ['D1', 'D2', 'D3', 'D4', 'D5', 'D6', 'D7'], hi: hi };
  }

  /* Evidence-graph markup for a metric. Clinical fig* take an opts object and
     go straight through Charts.render; the positional widget generators
     (gauge/barChart/calendarHeat) are dispatched explicitly so their figOpts
     map to real arguments instead of being swallowed as `score`. */
  function figMarkup(metric) {
    var fig = metric.fig, o = metric.figOpts || {}, seed = o.seed || 9;
    if (fig === 'gauge') {
      var col = statusColors(metric.status);
      return C.gauge(o.score != null ? o.score : 70, o.label || 'Coverage', o.statusTxt || '', col[0], col[1], o.max || 100);
    }
    if (fig === 'calendarHeat') return C.calendarHeat(o.days || seededHeat(seed));
    if (fig === 'barChart') { var b = seededBars(seed); return C.barChart(b.data, b.labels, b.hi, '#56ccf2', '#2f80ed'); }
    return (C.render ? C.render(fig, o) : '');
  }

  /* Split a patient summary into { body, rec, foot }: peels the trailing
     "(Monitoring … not a diagnosis.)" note and any "Recommended:" clause. */
  function splitSummary(text) {
    text = String(text == null ? '' : text);
    var foot = '';
    var m = text.match(/\(Monitoring[^)]*\)\s*$/i);
    if (m) { foot = m[0].replace(/^\(|\)\s*$/g, ''); text = text.slice(0, m.index).trim(); }
    var rec = '';
    var idx = text.indexOf('Recommended:');
    if (idx >= 0) { rec = text.slice(idx + 'Recommended:'.length).trim(); text = text.slice(0, idx).trim(); }
    return { body: text.trim(), rec: rec, foot: foot };
  }

  /* ============================================================ public UI */
  var UI = {};

  /* --- risk pill --------------------------------------------------------- */
  UI.riskPill = function (risk) {
    var r = riskRec(risk);
    return '<span class="rpill ' + r.cls + '">' + r.dot + ' ' + esc(r.label) + '</span>';
  };

  /* --- derived-estimate + confidence chip -------------------------------- */
  UI.confChip = function (level, provenance) {
    var lvl = confLevel(level);
    var lead = provenance ? esc(provenance) : 'derived estimate';
    return '<span class="confchip ' + lvl + '">◔ ' + lead + ' · ' + confLabel(level) + ' confidence</span>';
  };

  /* --- monitoring banner ------------------------------------------------- */
  UI.monitoringBanner = function () {
    return '<div class="mon-banner">' + esc(MONITORING) + '</div>';
  };

  /* --- pinned quick appointment summary ---------------------------------- */
  UI.quickSummary = function (patient) {
    if (!patient) return '';
    var r = riskRec(patient.risk);
    var s = splitSummary(patient.summary);
    var body = esc(s.body);
    if (s.rec) body += ' <span class="qs-rec">Recommended: ' + esc(s.rec) + '</span>';
    var title = esc(patient.name) + ' · ' + esc(patient.procShort || patient.procedure) +
      ' · Day ' + esc(patient.postopDay);
    return '<div class="quick-sum ' + r.cls + '">' +
      '<div class="qs-title">' + title + '</div>' +
      '<p class="qs-body">' + body + '</p>' +
      (s.foot ? '<div class="qs-foot">' + esc(s.foot) + '</div>' : '') +
      '</div>';
  };

  /* --- report object (a single metric card) ------------------------------ *
   * opts.gate === true wraps the card in tier gating (.gate.t2 / .gate.t3),
   * so the dashboard's tier toggle can progressively reveal deeper metrics.
   * Left off by default so surfaces without a tier toggle show everything.  */
  UI.reportObject = function (metric, opts) {
    if (!metric) return '';
    opts = opts || {};
    var status = metric.status || 'ok';
    var cls = 'report-card ' + status;
    if (opts.gate) { if (metric.tier === 2) cls += ' gate t2'; else if (metric.tier >= 3) cls += ' gate t3'; }

    var head = '<div class="ro-head">' +
      '<h4 class="ro-title">' + esc(metric.name) + '</h4>' +
      tierBadge(metric.tier || 1) +
      '<span class="stpill ' + status + '">' + esc(STATUS_WORD[status] || status) + '</span>' +
      '</div>';

    var vsBaseline = metric.statusText
      ? '<div class="rc-chips"><strong>Status vs baseline —</strong> ' + esc(metric.statusText) + '</div>'
      : '';

    var find = metric.finding ? '<p class="ro-find">' + esc(metric.finding) + '</p>' : '';
    var fig = '<div class="report-figs">' + figMarkup(metric) + '</div>';

    var chips = '<div class="ro-chiprow">' +
      (metric.derived ? '<span class="chip-in">◔ derived estimate</span>' : '') +
      UI.confChip(metric.confidence, metric.coverage) +
      '</div>';

    var guard = metric.guarded ? '<div class="gate-row">' + esc(GUARD_NOTE) + '</div>' : '';
    var next = metric.nextStep ? '<div class="ro-next">' + esc(metric.nextStep) + '</div>' : '';

    return '<div class="' + cls + '" data-mid="' + esc(metric.id) + '">' +
      head + vsBaseline + find + fig + chips + guard + next + '</div>';
  };

  /* --- profile card (groups a patient's metrics under one profile) ------- */
  UI.profileCard = function (profile, patient, opts) {
    if (!profile || !patient) return '';
    var metrics = (patient.metrics || []).filter(function (m) { return m.profileId === profile.id; });
    if (!metrics.length) return '';
    var inner = metrics.map(function (m) { return UI.reportObject(m, opts); }).join('');
    return '<section class="profile-card" data-profile="' + esc(profile.id) + '">' +
      '<div class="profile-head">' +
      '<h3>' + esc(profile.name) + '</h3>' +
      (profile.blurb ? '<p class="pf-blurb">' + esc(profile.blurb) + '</p>' : '') +
      '</div>' + inner + '</section>';
  };

  /* --- triage row -------------------------------------------------------- */
  UI.triageRow = function (patient) {
    if (!patient) return '';
    var high = patient.risk === 'high' ? ' high' : '';
    return '<div class="triage-row' + high + '" data-pid="' + esc(patient.id) + '">' +
      '<div class="tr-id">' +
      '<div class="tr-name">' + esc(patient.name) + '</div>' +
      '<div class="tr-proc">' + esc(patient.procShort || patient.procedure) + ' · Day ' + esc(patient.postopDay) + '</div>' +
      '</div>' +
      UI.riskPill(patient.risk) +
      '<div class="tr-why">' + esc(patient.whyFlagged) + '</div>' +
      UI.confChip(patient.dataConf, 'data coverage') +
      '<div class="tr-time">' + esc(patient.lastCheckin) + '</div>' +
      '</div>';
  };

  /* --- M17 trajectory-vs-expected block ---------------------------------- */
  UI.trajectoryBlock = function (patient) {
    if (!patient) return '';
    var traj = patient.trajectory || {};
    var state = traj.state || 'unknown';
    var map = {
      behind:     { pill: '<span class="pill warn">Behind curve</span>' },
      ahead:      { pill: '<span class="pill good">Ahead of curve</span>' },
      'on-track': { pill: '<span class="pill info">On track</span>' },
      unknown:    { pill: '<span class="pill" style="background:#f0f2f7;color:#7c879e">Insufficient data</span>' }
    };
    var head = (map[state] || map.unknown).pill;

    var m17 = findMetric(patient, 'M17');
    var fig = m17 ? figMarkup(m17) :
      (C.render ? C.render('figBaseline', { seed: (patient.seed || 41) + 17, band: 'expected CI', title: 'Recovery vs expected', note: 'Modeled expected-recovery band' }) : '');

    var pctChip = '';
    if (state !== 'unknown' && typeof traj.pct === 'number' && traj.pct !== 0) {
      pctChip = '<span class="chip-in">' + Math.abs(traj.pct) + '% ' + (traj.pct < 0 ? 'behind' : 'ahead of') + ' expected</span>';
    }
    var note = esc((m17 && m17.finding) || traj.note || '');

    return '<div class="card">' +
      '<div class="sec-title"><h3>Recovery trajectory</h3>' + head + '</div>' +
      '<div class="report-figs">' + fig + '</div>' +
      (note ? '<p class="ro-find">' + note + '</p>' : '') +
      '<div class="ro-chiprow">' + pctChip + UI.confChip((m17 && m17.confidence) || 'med', 'trajectory model') + '</div>' +
      '<div class="rc-methods"><strong>Method:</strong> modeled expected-recovery band for this procedure and age; shaded region ≈ 95% CI. Derived estimate — ' + esc(MONITORING) + '</div>' +
      '</div>';
  };

  /* --- M12/M13 deterioration index + driver matrix (GUARDED) ------------- */
  UI.driverBlock = function (patient) {
    if (!patient) return '';
    var m12 = findMetric(patient, 'M12'), m13 = findMetric(patient, 'M13');
    if (!m12 && !m13) return '';

    function panel(m) {
      if (!m) return '';
      return '<div class="block-label">' + esc(m.name) + '</div>' +
        '<p class="ro-find">' + esc(m.finding) + '</p>' +
        '<div class="report-figs">' + figMarkup(m) + '</div>' +
        '<div class="ro-chiprow">' +
        (m.derived ? '<span class="chip-in">◔ derived estimate</span>' : '') +
        UI.confChip(m.confidence, m.coverage) + '</div>' +
        (m.nextStep ? '<div class="ro-next">' + esc(m.nextStep) + '</div>' : '');
    }

    return '<div class="card">' +
      '<div class="sec-title"><h3>Complication surveillance</h3><span class="pill warn">Monitoring</span></div>' +
      '<div class="gate-row" style="margin-top:0;margin-bottom:11px">Multi-signal deviation from this patient’s own stable baseline — a prompt to review, never a conclusion.</div>' +
      panel(m12) + panel(m13) +
      '<div class="rc-methods"><strong>Guardrail:</strong> ' + esc(GUARD_NOTE) + '</div>' +
      '</div>';
  };

  /* --- M14 verified adherence (RTM-friendly) ----------------------------- */
  UI.adherenceBlock = function (patient) {
    if (!patient || !patient.adherence) return '';
    var a = patient.adherence;
    var pct = Math.round((a.rate || 0) * 100);
    var tone = pct >= 85 ? 'good' : (pct >= 70 ? 'info' : 'warn');
    var heat = C.calendarHeat ? C.calendarHeat(a.days || seededHeat(patient.seed)) : '';
    return '<div class="card">' +
      '<div class="sec-title"><h3>Verified adherence</h3><span class="pill ' + tone + '">' + pct + '% verified</span></div>' +
      '<div class="report-figs" style="align-items:center">' + heat + '</div>' +
      '<div class="legendrow"><span><i style="background:#0c9d57"></i>device-verified</span><span><i style="background:#e6f5e9"></i>self-attested / none</span></div>' +
      '<div class="kv"><span class="k">Device-verified tasks</span><span class="v">' + esc(a.verified) + ' of ' + esc(a.assigned) + '</span></div>' +
      '<div class="kv"><span class="k">Self-attested</span><span class="v">' + esc(a.selfAttested) + '</span></div>' +
      '<div class="kv"><span class="k">Completion (verified)</span><span class="v">' + pct + '%</span></div>' +
      (a.note ? '<div class="rc-chips">' + esc(a.note) + '</div>' : '') +
      '</div>';
  };

  /* --- RTM documentation summary (auditable) ----------------------------- */
  UI.rtmBlock = function (patient) {
    if (!patient) return '';
    var a = patient.adherence || {};
    var days = a.days || [];
    var deviceDays = 0, i;
    for (i = 0; i < days.length; i++) if (days[i] > 0) deviceDays++;
    var interactions = ((patient.conversation || []).filter(function (t) { return t.who === 'copilot'; })).length;
    var meets = deviceDays >= 16;
    return '<div class="card">' +
      '<div class="sec-title"><h3>RTM documentation</h3><span class="pill info">Auditable</span></div>' +
      '<div class="kv"><span class="k">Enrolment</span><span class="v">' + esc(patient.procShort || patient.procedure) + ' · post-op day ' + esc(patient.postopDay) + '</span></div>' +
      '<div class="kv"><span class="k">Device-supplied days (period)</span><span class="v">' + deviceDays + ' <span class="pill ' + (meets ? 'good' : 'warn') + '">' + (meets ? '≥16 met' : 'under 16') + '</span></span></div>' +
      '<div class="kv"><span class="k">Days with usable monitoring data</span><span class="v">' + deviceDays + '</span></div>' +
      '<div class="kv"><span class="k">Interactive check-ins logged</span><span class="v">' + interactions + '</span></div>' +
      '<div class="kv"><span class="k">Last sync / check-in</span><span class="v">' + esc(patient.lastCheckin) + '</span></div>' +
      '<div class="kv"><span class="k">Data confidence (M16)</span><span class="v">' + confLabel(patient.dataConf) + '</span></div>' +
      '<div class="rc-methods">RTM (98975–98978) documentation view — auditable device-supplied days, treatment-management time and interactive communication. Illustrative sample data.</div>' +
      '</div>';
  };

  /* --- conversation history (chat bubbles) ------------------------------- */
  UI.conversationBlock = function (patient) {
    if (!patient) return '';
    var turns = patient.conversation || [];
    var bubbles = turns.map(function (t) {
      var side = t.who === 'patient' ? 'me' : 'bot';
      return '<div class="bubble ' + side + '">' + esc(t.text) + '</div>';
    }).join('');
    return '<div class="card">' +
      '<div class="sec-title"><h3>Check-in conversation</h3><span class="pill prem">Copilot</span></div>' +
      '<div style="display:flex;flex-direction:column">' + bubbles + '</div>' +
      '</div>';
  };

  /* --- tier controller (wires .segment Everyday/Advanced/Clinical) ------- *
   * Acts on the DOM. rootEl may be the .segment element itself, a container
   * that holds one, or null (falls back to the first .segment in document).
   * Sets body.tier-1/2/3 exactly like the reference, preserving any other
   * body classes (e.g. `fluid`).                                            */
  function setBodyTier(t) {
    t = String(t || '1').replace(/[^123]/g, '') || '1';
    var b = global.document.body;
    var cls = (b.className || '').replace(/\btier-[123]\b/g, '').replace(/\s+/g, ' ').trim();
    b.className = (cls + ' tier-' + t).trim();
  }
  UI.tierController = function (rootEl) {
    var doc = global.document;
    var seg = null;
    if (!rootEl) seg = doc.querySelector('.segment');
    else if (rootEl.classList && rootEl.classList.contains('segment')) seg = rootEl;
    else if (rootEl.querySelector) seg = rootEl.querySelector('.segment');
    if (!seg) return null;

    var onBtn = seg.querySelector('button.on') || seg.querySelector('button');
    if (onBtn) {
      seg.querySelectorAll('button').forEach(function (x) { x.classList.remove('on'); });
      onBtn.classList.add('on');
      setBodyTier(onBtn.dataset.tier || '1');
    }
    seg.addEventListener('click', function (e) {
      var b = e.target && e.target.closest ? e.target.closest('button') : null;
      if (!b || !seg.contains(b)) return;
      seg.querySelectorAll('button').forEach(function (x) { x.classList.remove('on'); });
      b.classList.add('on');
      setBodyTier(b.dataset.tier || '1');
      if (global.Bus && global.Bus.emit) { try { global.Bus.emit('DEMO_STEP', { tier: b.dataset.tier }); } catch (err) { /* non-fatal */ } }
    });
    return seg;
  };

  /* --- toast (glass, top-right, slide-in) — SMS-to-clinician demo -------- *
   * Acts on the DOM. opts: { glass, timeout(ms; 0/false = persistent),
   * danger }. Toasts stack inside a shared .toast-wrap. Returns the element. */
  function ensureToastWrap() {
    var doc = global.document, w = doc.querySelector('.toast-wrap');
    if (!w) { w = doc.createElement('div'); w.className = 'toast-wrap'; doc.body.appendChild(w); }
    return w;
  }
  UI.showToast = function (html, opts) {
    opts = opts || {};
    var doc = global.document;
    var wrap = ensureToastWrap();
    var t = doc.createElement('div');
    t.className = 'toast' + (opts.danger ? ' danger' : '');
    t.innerHTML = html;
    wrap.appendChild(t);
    var kill = function () {
      if (t._killed) return; t._killed = true;
      t.classList.add('out');
      global.setTimeout(function () { if (t.parentNode) t.parentNode.removeChild(t); }, 380);
    };
    var timeout = (opts.timeout == null) ? 4200 : opts.timeout;
    if (timeout) global.setTimeout(kill, timeout);
    t.addEventListener('click', kill);
    return t;
  };

  /* --- patient task card (qualitative completion only · NO numbers) ------ */
  function taskIndicator(task) {
    if (task && task.done) {
      return '<svg width="34" height="34" viewBox="0 0 34 34" aria-hidden="true">' +
        '<circle cx="17" cy="17" r="15" fill="#0a9d57"/>' +
        '<path d="M10.6 17.6 L15.1 22 L23.2 12.6" fill="none" stroke="#fff" stroke-width="2.7" stroke-linecap="round" stroke-linejoin="round"/></svg>';
    }
    var p = Math.max(0, Math.min(1, (task && task.ring) || 0));
    var R = 15, C2 = 2 * Math.PI * R;
    var prog = p > 0
      ? '<circle cx="17" cy="17" r="15" fill="none" stroke="#1fd17a" stroke-width="3.2" stroke-linecap="round" stroke-dasharray="' + (p * C2).toFixed(1) + ' ' + C2.toFixed(1) + '" transform="rotate(-90 17 17)"/>'
      : '';
    return '<svg width="34" height="34" viewBox="0 0 34 34" aria-hidden="true">' +
      '<circle cx="17" cy="17" r="15" fill="none" stroke="#e3e9f4" stroke-width="3.2"/>' + prog + '</svg>';
  }
  UI.taskCard = function (task) {
    if (!task) return '';
    return '<div class="card tight" data-task="' + esc(task.title) + '"' + (task.done ? ' data-done="1"' : '') + '>' +
      '<div class="row">' +
      '<div style="flex:0 0 auto">' + taskIndicator(task) + '</div>' +
      '<div style="flex:1;min-width:0">' +
      '<h2 style="font-size:14.5px">' + esc(task.title) + '</h2>' +
      '<p class="sub" style="margin:2px 0 0">' + esc(task.why) + '</p>' +
      '</div>' +
      '</div>' +
      '</div>';
  };

  global.UI = UI;
})(typeof window !== 'undefined' ? window : this);
