'use strict';
var $ = function (s, r) { return (r || document).querySelector(s); };
var $$ = function (s, r) { return Array.prototype.slice.call((r || document).querySelectorAll(s)); };

var STEPS = ['s1', 's2', 's3', 's4', 's5'];

var state = {
  step: 's1',
  pulled: false,
  clearanceTouched: false,
  followTouched: false,
  screening: { q1: 'yes', q2: 'yes', q3: 'no', q4: 'no', q5: 'no' },
  meds: [
    { name: 'Metformin', dose: '1000 mg', freq: 'Twice daily', filled: 'May 2024', status: 'Confirm', pill: 'pill-green', actions: ['Continue', 'Modify', 'Discontinue'], action: 'Continue', fixed: true },
    { name: 'Lisinopril', dose: '10 mg', freq: 'Once daily', filled: 'Nov 2023', status: 'Review', pill: 'pill-amber', actions: ['Continue', 'Modify dose', 'Discontinue'], action: 'Continue', fixed: true },
    { name: 'Sertraline', dose: '100 mg', freq: 'Once daily', filled: 'Aug 2023', status: 'Gap >60d', pill: 'pill-amber', actions: ['Restart', 'Modify', 'Psychiatry referral'], action: 'Restart', fixed: true },
    { name: 'Insulin (PRN)', dose: 'Per sliding scale', freq: 'As needed', filled: '—', status: 'Order needed', pill: 'pill-red', actions: ['Order sliding scale', 'Defer to physician'], action: 'Order sliding scale', fixed: true }
  ]
};

var QUESTIONS = [
  { id: 'q1', text: 'Have you ever been diagnosed with a mental health condition?', opts: ['yes', 'no'] },
  { id: 'q2', text: 'Are you currently taking any psychiatric medications?', opts: ['yes', 'no'] },
  { id: 'q3', text: 'In the past 2 weeks, have you had thoughts of harming yourself?', opts: ['yes', 'no'] },
  { id: 'q4', text: 'Have you ever attempted suicide?', opts: ['yes', 'no'] },
  { id: 'q5', text: 'Are you currently experiencing withdrawal symptoms?', opts: ['yes', 'no', 'unk'] }
];

var PULLED_FLAGS = [
  { cls: 'amber', html: '<b>Type 2 Diabetes</b> — last HbA1c 8.4% (Jun 2023, Memorial Health). Metformin 1000mg prescribed. Insulin PRN noted. <span class="tag">Chronic</span>' },
  { cls: 'red', html: '<b>Hypertension</b> — Lisinopril 10mg, last BP 158/94 (Nov 2023). Not consistently controlled. <span class="tag">Active</span>' },
  { cls: 'amber', html: '<b>Major Depressive Disorder</b> — Sertraline 100mg prescribed, last refill Aug 2023. Documented prior hospitalization 2021. <span class="tag">Psychiatric</span>' },
  { cls: 'green', html: '<b>No known drug allergies</b> — confirmed across all 3 records.' }
];

/* ---------- toasts ---------- */
function toast(msg, type) {
  var t = document.createElement('div');
  t.className = 'toast' + (type === 'err' ? ' err' : '');
  t.innerHTML = '<span class="t-ic">' + (type === 'err' ? '⚠' : '✓') + '</span><span></span>';
  t.lastChild.textContent = msg;
  $('#toastWrap').appendChild(t);
  setTimeout(function () {
    t.classList.add('out');
    setTimeout(function () { t.remove(); }, 320);
  }, 3200);
}

/* ---------- step navigation ---------- */
function show(id) {
  state.step = id;
  $$('.screen').forEach(function (s) { s.classList.remove('active'); });
  $('#' + id).classList.add('active');
  var idx = STEPS.indexOf(id);
  $$('.step').forEach(function (st, i) {
    st.classList.remove('active', 'done');
    var num = st.querySelector('.snum');
    if (i < idx) { st.classList.add('done'); num.textContent = '✓'; }
    else { num.textContent = String(i + 1); if (i === idx) st.classList.add('active'); }
  });
  window.scrollTo(0, 0);
  if (id === 's2' && !state.pulled) runPull();
  if (id === 's5') buildSummary();
}

function runPull() {
  state.pulled = true;
  setTimeout(function () {
    var wrap = $('#pullFlags');
    wrap.innerHTML = '';
    PULLED_FLAGS.forEach(function (f, i) {
      var el = document.createElement('div');
      el.className = 'flag ' + f.cls;
      el.style.animationDelay = (i * 90) + 'ms';
      el.innerHTML = '<div class="flag-dot"></div><div class="flag-text">' + f.html + '</div>';
      wrap.appendChild(el);
    });
    $('#pullPill').textContent = '3 sources matched';
    toast('Records pulled — 3 sources matched');
  }, 850);
}

/* ---------- vitals ---------- */
function parseBp(v) {
  var m = String(v).match(/(\d+)\s*\/\s*(\d+)/);
  return m ? { sys: +m[1], dia: +m[2] } : null;
}
function parseNum(v) {
  var m = String(v).match(/(\d+(\.\d+)?)/);
  return m ? +m[1] : null;
}
function vitalsStatus() {
  var bp = parseBp($('#vBp').value);
  var glu = parseNum($('#vGlu').value);
  return {
    bp: bp,
    glu: glu,
    bpLevel: !bp ? 'none' : (bp.sys >= 160 || bp.dia >= 95) ? 'red' : (bp.sys >= 140 || bp.dia >= 90) ? 'amber' : 'ok',
    gluLevel: glu == null ? 'none' : glu >= 250 ? 'red' : glu > 180 ? 'amber' : 'ok'
  };
}
function styleVitals() {
  var v = vitalsStatus();
  var bpEl = $('#vBp'), gluEl = $('#vGlu');
  bpEl.classList.remove('warn-red', 'warn-amber');
  gluEl.classList.remove('warn-red', 'warn-amber');
  if (v.bpLevel === 'red') bpEl.classList.add('warn-red');
  if (v.bpLevel === 'amber') bpEl.classList.add('warn-amber');
  if (v.gluLevel === 'red') gluEl.classList.add('warn-red');
  if (v.gluLevel === 'amber') gluEl.classList.add('warn-amber');
  updateRisk();
}

/* ---------- medications ---------- */
function renderMeds() {
  var tb = $('#medRows');
  tb.innerHTML = '';
  state.meds.forEach(function (m, i) {
    var tr = document.createElement('tr');
    if (m.isNew) tr.className = 'med-new';
    var td1 = document.createElement('td'); td1.innerHTML = '<b></b>'; td1.firstChild.textContent = m.name; tr.appendChild(td1);
    var td2 = document.createElement('td'); td2.textContent = m.dose; tr.appendChild(td2);
    var td3 = document.createElement('td'); td3.textContent = m.freq; tr.appendChild(td3);
    var td4 = document.createElement('td'); td4.textContent = m.filled; tr.appendChild(td4);
    var td5 = document.createElement('td');
    td5.innerHTML = '<span class="pill ' + m.pill + '"></span>';
    td5.firstChild.textContent = m.status;
    tr.appendChild(td5);
    var td6 = document.createElement('td');
    var sel = document.createElement('select');
    m.actions.forEach(function (a) {
      var o = document.createElement('option'); o.textContent = a; if (a === m.action) o.selected = true; sel.appendChild(o);
    });
    sel.addEventListener('change', function () { m.action = sel.value; toast(m.name + ' → ' + sel.value); });
    td6.appendChild(sel);
    if (!m.fixed) {
      var rm = document.createElement('button');
      rm.className = 'med-remove'; rm.title = 'Remove'; rm.textContent = '✕';
      rm.addEventListener('click', function () {
        state.meds.splice(i, 1); renderMeds(); toast(m.name + ' removed');
      });
      td6.appendChild(rm);
    }
    tr.appendChild(td6);
    tb.appendChild(tr);
  });
}
function addMed() {
  var name = $('#amName').value.trim();
  var dose = $('#amDose').value.trim();
  var bad = false;
  if (!name) { flashErr($('#amName')); bad = true; }
  if (!dose) { flashErr($('#amDose')); bad = true; }
  if (bad) { toast('Medication name and dose are required', 'err'); return; }
  state.meds.push({
    name: name, dose: dose, freq: $('#amFreq').value, filled: 'New order',
    status: 'New order', pill: 'pill-blue',
    actions: ['Continue', 'Hold', 'Discontinue'], action: 'Continue',
    route: $('#amRoute').value, isNew: true
  });
  renderMeds();
  $('#amName').value = ''; $('#amDose').value = '';
  toast(name + ' added to medication list');
}
function flashErr(el) {
  el.classList.remove('shake');
  void el.offsetWidth;
  el.classList.add('shake');
  setTimeout(function () { el.classList.remove('shake'); }, 400);
}

/* ---------- screening + risk ---------- */
function renderQuestions() {
  var wrap = $('#aiQuestions');
  wrap.innerHTML = '';
  QUESTIONS.forEach(function (q) {
    var row = document.createElement('div');
    row.className = 'ai-q';
    var txt = document.createElement('div'); txt.className = 'ai-q-text'; txt.textContent = q.text;
    var opts = document.createElement('div'); opts.className = 'ai-q-opts';
    q.opts.forEach(function (val) {
      var b = document.createElement('button');
      b.className = 'aq-btn';
      b.textContent = val === 'yes' ? 'Yes' : val === 'no' ? 'No' : 'Unsure';
      b.setAttribute('data-q', q.id);
      b.setAttribute('data-val', val);
      if (state.screening[q.id] === val) b.classList.add(val === 'yes' ? 'sel-yes' : val === 'no' ? 'sel-no' : 'sel-unk');
      b.addEventListener('click', function () {
        state.screening[q.id] = val;
        $$('.aq-btn', opts).forEach(function (x) { x.className = 'aq-btn'; });
        b.classList.add(val === 'yes' ? 'sel-yes' : val === 'no' ? 'sel-no' : 'sel-unk');
        updateRisk();
      });
      opts.appendChild(b);
    });
    row.appendChild(txt); row.appendChild(opts);
    wrap.appendChild(row);
  });
}

function riskModel() {
  var s = state.screening, v = vitalsStatus();
  var score = 20;
  var factors = [];
  if (s.q1 === 'yes') { score += 10; factors.push('MDD history'); }
  if (s.q2 === 'yes') { score += 5; }
  factors.push('sertraline gap >60 days');
  if (s.q3 === 'yes') { score += 45; factors.push('active suicidal ideation reported'); }
  if (s.q4 === 'yes') { score += 20; factors.push('prior suicide attempt'); }
  if (s.q5 === 'yes') { score += 18; factors.push('active withdrawal symptoms'); }
  if (s.q5 === 'unk') { score += 9; factors.push('possible withdrawal (unconfirmed)'); }
  if (v.bpLevel === 'red' || v.bpLevel === 'amber') { score += 5; }
  if (v.gluLevel === 'red' || v.gluLevel === 'amber') { score += 5; factors.push('elevated glucose at intake'); }
  score = Math.min(score, 96);
  var level = score >= 65 ? 'High' : score >= 35 ? 'Moderate' : 'Low';
  return { score: score, level: level, factors: factors, si: s.q3 === 'yes' };
}

function updateRisk() {
  var r = riskModel();
  var bar = $('#riskBar'), lbl = $('#riskLbl');
  var color = r.level === 'High' ? '#ef4444' : r.level === 'Moderate' ? '#f59e0b' : '#22c55e';
  bar.style.width = r.score + '%';
  bar.style.background = color;
  lbl.textContent = r.level;
  lbl.style.color = color;
  var rec = r.si
    ? 'Recommend <b>mental health hold with 1:1 observation</b> and <b>psychiatric evaluation within 24h</b>.'
    : r.level === 'High'
      ? 'Recommend <b>psychiatric evaluation within 24h</b> and <b>diabetic care plan on admission</b>.'
      : 'Recommend <b>psychiatric follow-up within 72h</b> and <b>diabetic care plan on admission</b>.';
  $('#riskNote').innerHTML = 'Based on ' + r.factors.join(', ') + '. ' + rec;
  if (!state.clearanceTouched) {
    $('#clrHousing').value = r.si ? 'Mental health hold' : r.level === 'High' ? 'Medical unit hold' : 'Cleared — general population';
  }
  if (!state.followTouched) {
    $('#clrFollow').value = (r.si || r.level === 'High') ? '24 hours' : '72 hours';
  }
  housingWarn();
}

function housingWarn() {
  var r = riskModel();
  var bad = r.si && /general population/i.test($('#clrHousing').value);
  $('#housingWarn').classList.toggle('show', bad);
}

/* ---------- summary ---------- */
function medByName(n) {
  for (var i = 0; i < state.meds.length; i++) if (state.meds[i].name.indexOf(n) === 0) return state.meds[i];
  return null;
}
function buildSummary() {
  var last = $('#dLast').value.trim() || 'Torres';
  var first = $('#dFirst').value.trim() || 'Miguel';
  $('#sumTitle').textContent = last + ', ' + first + ' — Intake summary';

  var v = vitalsStatus();
  var r = riskModel();
  var flags = [];

  if (v.bpLevel === 'red') flags.push({ cls: 'red', html: '<b>BP ' + esc($('#vBp').value) + '</b> — uncontrolled hypertension at intake. Lisinopril dose review needed.' });
  else if (v.bpLevel === 'amber') flags.push({ cls: 'amber', html: '<b>BP ' + esc($('#vBp').value) + '</b> — elevated at intake. Recheck within 24h; continue Lisinopril.' });
  else flags.push({ cls: 'green', html: '<b>BP ' + esc($('#vBp').value) + '</b> — within acceptable range at intake.' });

  if (v.gluLevel !== 'ok' && v.gluLevel !== 'none') {
    var ins = medByName('Insulin');
    var insTxt = ins && ins.action === 'Defer to physician' ? 'Deferred to physician for insulin orders.' : 'Sliding scale insulin ordered.';
    flags.push({ cls: v.gluLevel === 'red' ? 'red' : 'amber', html: '<b>Glucose ' + esc($('#vGlu').value) + '</b> — ' + insTxt + ' Diabetic care plan required.' });
  } else {
    flags.push({ cls: 'green', html: '<b>Glucose ' + esc($('#vGlu').value) + '</b> — within range. Continue Metformin per reconciliation.' });
  }

  var ser = medByName('Sertraline');
  if (ser) {
    var serMap = {
      'Restart': 'Restart ordered at 100 mg daily.',
      'Modify': 'Dose modification pending clinician review.',
      'Psychiatry referral': 'Psychiatry referral placed.'
    };
    flags.push({ cls: 'amber', html: '<b>Sertraline gap &gt;60 days</b> — MDD history. ' + (serMap[ser.action] || ser.action + '.') + ' 72h follow-up flagged.' });
  }

  if (r.si) flags.push({ cls: 'red', html: '<b>SI reported</b> — mental health hold required. 1:1 observation and psychiatric evaluation within 24h.' });
  else flags.push({ cls: 'green', html: '<b>No SI reported</b> — standard housing cleared. Monitor per protocol.' });

  if (state.screening.q4 === 'yes' && !r.si) flags.push({ cls: 'amber', html: '<b>Prior suicide attempt history</b> — enhanced monitoring per protocol; psychiatry notified.' });
  if (state.screening.q5 === 'yes') flags.push({ cls: 'amber', html: '<b>Withdrawal symptoms reported</b> — initiate CIWA/COWS monitoring q4h.' });
  if (state.screening.q5 === 'unk') flags.push({ cls: 'amber', html: '<b>Possible withdrawal (unconfirmed)</b> — observe and re-screen within 4h.' });

  var added = state.meds.filter(function (m) { return m.isNew; });
  if (added.length) flags.push({ cls: 'amber', html: '<b>' + added.length + ' new medication order' + (added.length > 1 ? 's' : '') + '</b> — ' + esc(added.map(function (m) { return m.name + ' ' + m.dose; }).join(', ')) + ' pending physician co-sign.' });

  var wrap = $('#sumFlags');
  wrap.innerHTML = '';
  flags.forEach(function (f, i) {
    var el = document.createElement('div');
    el.className = 'flag ' + f.cls;
    el.style.animationDelay = (i * 70) + 'ms';
    el.innerHTML = '<div class="flag-dot"></div><div class="flag-text">' + f.html + '</div>';
    wrap.appendChild(el);
  });
  housingWarn();
}
function esc(s) {
  return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

/* ---------- submit / print ---------- */
function submitIntake() {
  if (!$('#clrSign').value.trim()) {
    flashErr($('#clrSign'));
    toast('Signing clinician is required', 'err');
    return;
  }
  var r = riskModel();
  $('#modalBody').innerHTML =
    '<b>' + esc($('#dLast').value) + ', ' + esc($('#dFirst').value) + '</b> — Booking #2024-0614<br/>' +
    'Risk level: <b>' + r.level + '</b><br/>' +
    'Housing: <b>' + esc($('#clrHousing').value) + '</b><br/>' +
    'Follow-up: <b>within ' + esc($('#clrFollow').value) + '</b><br/>' +
    'Signed: <b>' + esc($('#clrSign').value) + '</b>';
  $('#modalOverlay').classList.add('show');
}
function closeModal() { $('#modalOverlay').classList.remove('show'); }

/* ---------- wire up ---------- */
function init() {
  $$('.step').forEach(function (st) {
    st.addEventListener('click', function () { show(st.getAttribute('data-step')); });
  });
  $$('[data-go]').forEach(function (b) {
    b.addEventListener('click', function () { show(b.getAttribute('data-go')); });
  });
  ['vBp', 'vGlu', 'vHr', 'vTemp', 'vO2', 'vWt'].forEach(function (id) {
    $('#' + id).addEventListener('input', styleVitals);
  });
  $('#amAdd').addEventListener('click', addMed);
  $('#clrHousing').addEventListener('change', function () { state.clearanceTouched = true; housingWarn(); });
  $('#clrFollow').addEventListener('change', function () { state.followTouched = true; });
  $('#submitBtn').addEventListener('click', submitIntake);
  $('#printBtn').addEventListener('click', function () {
    try { window.print(); } catch (e) { toast('Print is unavailable in this environment', 'err'); }
  });
  $('#modalClose').addEventListener('click', closeModal);
  $('#modalNew').addEventListener('click', function () { window.location.reload(); });
  $('#modalOverlay').addEventListener('click', function (e) { if (e.target === this) closeModal(); });

  renderMeds();
  renderQuestions();
  styleVitals();
  show('s1');
}
if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
else init();
