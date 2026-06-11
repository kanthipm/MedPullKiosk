'use strict';
var $ = function (s, r) { return (r || document).querySelector(s); };
var $$ = function (s, r) { return Array.prototype.slice.call((r || document).querySelectorAll(s)); };

/* 6 facility-wide shift candidates: 3 processed earlier today (offscreen), 3 actionable below */
var PRIOR_APPLIED = 3;
var SESSIONS_PER_SHIFT = 2;

var patients = [
  { id: 'chen', name: 'Chen, Wei', dob: '1948', unit: '4B', ktv: '1.8', pct: 90, color: '#22c55e', bun: '42 mg/dL', rkf: '2.1 mL/min', kind: 'shift', applied: false,
    week2x: ['dial', 'free', 'skip', 'dial', 'free'] },
  { id: 'okonkwo', name: 'Okonkwo, Ada', dob: '1955', unit: '2A', ktv: '1.7', pct: 85, color: '#22c55e', bun: '38 mg/dL', rkf: '1.8 mL/min', kind: 'shift', applied: false,
    week2x: ['free', 'dial', 'skip', 'free', 'dial'] },
  { id: 'rivera', name: 'Rivera, Carlos', dob: '1961', unit: '3C', ktv: '1.6', pct: 80, color: '#84cc16', bun: '45 mg/dL', rkf: '1.5 mL/min', kind: 'shift', applied: false,
    week2x: ['dial', 'skip', 'free', 'dial', 'skip'] },
  { id: 'patel', name: 'Patel, Sunita', dob: '1950', unit: '1A', ktv: '1.3', pct: 65, color: '#f59e0b', bun: '58 mg/dL', rkf: '0.6 mL/min', kind: 'maintain', applied: false },
  { id: 'johnson', name: 'Johnson, Mae', dob: '1943', unit: '2B', ktv: '1.2', pct: 55, color: '#ef4444', bun: '72 mg/dL', rkf: '0.3 mL/min', kind: 'escalate', flagged: false },
  { id: 'kim', name: 'Kim, David', dob: '1958', unit: '5A', ktv: '—', pct: 0, color: '#e5e7eb', bun: 'Pending', rkf: 'Pending', kind: 'pending', held: false }
];
var WEEK_STD = ['dial', 'skip', 'dial', 'skip', 'dial'];

var crm = [
  { id: 'samuels', name: 'Dr. R. Samuels', sub: 'Primary contact', practice: 'Memorial Nephrology', pts: '8–10 pts', last: 'Today', status: 'In meeting', pillCls: 'p-blue', action: 'pitch', label: 'View pitch', done: false },
  { id: 'nguyen', name: 'Dr. L. Nguyen', sub: '', practice: 'Westside Renal Care', pts: '5–7 pts', last: '3 weeks ago', status: 'Follow up', pillCls: 'p-amber', action: 'email', label: 'Send email', done: false },
  { id: 'osei', name: 'Dr. A. Osei', sub: '', practice: 'University Kidney Clinic', pts: '10–12 pts', last: 'Never', status: 'Cold outreach', pillCls: 'p-gray', action: 'intro', label: 'Draft intro', done: false },
  { id: 'flores', name: 'Dr. M. Flores', sub: '', practice: 'Southwest Dialysis Group', pts: '6–8 pts', last: '6 weeks ago', status: 'Re-engage', pillCls: 'p-red', action: 'update', label: 'Send update', done: false }
];

/* ---------- derived math ---------- */
function appliedVisible() { return patients.filter(function (p) { return p.kind === 'shift' && p.applied; }).length; }
function shiftedTotal() { return PRIOR_APPLIED + appliedVisible(); }
function freedSessions() { return shiftedTotal() * SESSIONS_PER_SHIFT; }
function money(n) { return '$' + n.toLocaleString('en-US'); }

/* ---------- helpers ---------- */
function toast(msg, type) {
  var t = document.createElement('div');
  t.className = 'toast' + (type === 'err' ? ' err' : '');
  t.innerHTML = '<span>' + (type === 'err' ? '⚠' : '✓') + '</span><span></span>';
  t.lastChild.textContent = msg;
  $('#toastWrap').appendChild(t);
  setTimeout(function () {
    t.classList.add('out');
    setTimeout(function () { t.remove(); }, 320);
  }, 3200);
}
function bump(el) {
  el.classList.remove('bump');
  void el.offsetWidth;
  el.classList.add('bump');
}
function setStat(id, text) {
  var el = $('#' + id);
  if (el.textContent !== text) { el.textContent = text; bump(el); }
}

/* ---------- tabs ---------- */
function showTab(id) {
  $$('.screen').forEach(function (s) { s.classList.remove('active'); });
  $('#tab-' + id).classList.add('active');
  $$('.tab').forEach(function (t) { t.classList.toggle('active', t.getAttribute('data-tab') === id); });
  if (id === 'chairs') renderWeekGrid();
  window.scrollTo(0, 0);
}

/* ---------- patient table ---------- */
function renderPatients() {
  var tb = $('#patientRows');
  tb.innerHTML = '';
  patients.forEach(function (p) {
    var tr = document.createElement('tr');
    if (p.kind === 'shift') tr.className = p.applied ? 'applied-row' : 'highlight-row';

    var td1 = document.createElement('td');
    td1.innerHTML = '<b></b><div class="td-sub"></div>';
    td1.firstChild.textContent = p.name;
    td1.lastChild.textContent = 'DOB ' + p.dob + ' · Unit ' + p.unit;
    tr.appendChild(td1);

    var td2 = document.createElement('td');
    td2.innerHTML = '<div class="lab-bar-wrap">' + p.ktv + '<div class="lab-bar-bg"><div class="lab-bar-fill" style="background:' + p.color + '"></div></div></div>';
    tr.appendChild(td2);
    setTimeout(function () { td2.querySelector('.lab-bar-fill').style.width = p.pct + '%'; }, 40);

    var td3 = document.createElement('td'); td3.textContent = p.bun; tr.appendChild(td3);
    var td4 = document.createElement('td'); td4.textContent = p.rkf; tr.appendChild(td4);

    var td5 = document.createElement('td');
    var schedPill = document.createElement('span');
    if (p.kind === 'shift' && p.applied) { schedPill.className = 'pill p-teal swap'; schedPill.textContent = '2× / week'; }
    else if (p.kind === 'maintain' || p.kind === 'escalate') { schedPill.className = 'pill p-blue'; schedPill.textContent = '3× / week'; }
    else { schedPill.className = 'pill p-gray'; schedPill.textContent = '3× / week'; }
    td5.appendChild(schedPill);
    tr.appendChild(td5);

    var td6 = document.createElement('td');
    var rec = document.createElement('span');
    if (p.kind === 'shift') {
      rec.className = 'pill p-green' + (p.applied ? ' swap' : '');
      rec.textContent = p.applied ? '✓ Applied — 2×/week' : '✓ Shift to 2×/week';
    } else if (p.kind === 'maintain') { rec.className = 'pill p-amber'; rec.textContent = 'Maintain 3×/week'; }
    else if (p.kind === 'escalate') { rec.className = 'pill p-red'; rec.textContent = '↑ Consider 4×/week'; }
    else { rec.className = 'pill p-gray'; rec.textContent = p.held ? 'On hold — labs pending' : 'Awaiting labs'; }
    td6.appendChild(rec);
    tr.appendChild(td6);

    var td7 = document.createElement('td');
    var btn = document.createElement('button');
    if (p.kind === 'shift') {
      btn.className = p.applied ? 'action-btn' : 'action-btn action-btn-primary';
      btn.textContent = p.applied ? 'Undo' : 'Apply';
      btn.addEventListener('click', function () {
        p.applied = !p.applied;
        renderPatients();
        updateDerived();
        toast(p.applied ? 'Schedule updated for ' + p.name + ' — 2×/week (2 sessions freed)' : 'Reverted ' + p.name + ' to 3×/week');
      });
    } else if (p.kind === 'maintain') {
      btn.className = 'action-btn action-btn-muted';
      btn.textContent = 'No change';
      btn.addEventListener('click', function () { toast('No schedule change recommended for ' + p.name); });
    } else if (p.kind === 'escalate') {
      btn.className = 'action-btn action-btn-danger' + (p.flagged ? ' on' : '');
      btn.textContent = p.flagged ? 'Flagged ✓' : 'Flag';
      btn.addEventListener('click', function () {
        p.flagged = !p.flagged;
        renderPatients();
        updateDerived();
        toast(p.flagged ? p.name + ' flagged for nephrologist review — 4×/week consideration' : 'Flag removed for ' + p.name);
      });
    } else {
      btn.className = 'action-btn';
      btn.textContent = p.held ? 'Held ✓' : 'Hold';
      btn.addEventListener('click', function () {
        p.held = !p.held;
        renderPatients();
        toast(p.held ? p.name + ' placed on hold until labs arrive' : 'Hold released for ' + p.name);
      });
    }
    td7.appendChild(btn);
    tr.appendChild(td7);
    tb.appendChild(tr);
  });
}

/* ---------- week grid ---------- */
function renderWeekGrid() {
  var grid = $('#weekGrid');
  grid.innerHTML = '';
  var head = ['Patient', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri'];
  head.forEach(function (h) {
    var c = document.createElement('div');
    c.className = 'wg-cell wg-header';
    c.textContent = h;
    grid.appendChild(c);
  });
  patients.forEach(function (p) {
    if (p.kind === 'pending') return;
    var nameCell = document.createElement('div');
    nameCell.className = 'wg-cell wg-name';
    nameCell.textContent = p.name;
    grid.appendChild(nameCell);
    var days = (p.kind === 'shift' && p.applied) ? p.week2x : WEEK_STD;
    days.forEach(function (d) {
      var cell = document.createElement('div');
      cell.className = 'wg-cell';
      var slot = document.createElement('div');
      if (d === 'dial' && p.kind === 'shift' && p.applied) { slot.className = 'wg-slot slot-2x'; slot.textContent = '✓ Dial'; }
      else if (d === 'dial') { slot.className = 'wg-slot slot-std'; slot.textContent = 'Dial'; }
      else if (d === 'free') { slot.className = 'wg-slot slot-free'; slot.textContent = 'Free'; }
      else { slot.className = 'wg-slot slot-skip'; slot.textContent = '—'; }
      cell.appendChild(slot);
      grid.appendChild(cell);
    });
  });
}

/* ---------- derived stats ---------- */
function updateDerived() {
  var freed = freedSessions();
  var shifted = shiftedTotal();
  var flagged = patients.filter(function (p) { return p.flagged; }).length;

  $('#applyProgress').textContent = shifted + ' of 6 applied today';
  setStat('statPending', String(2 + flagged));

  $('#ctaText').textContent = shifted + ' patients shifting to 2×/week this cycle';
  $('#ctaSub').textContent = 'Estimated ' + freed + ' chair-sessions freed per week → available for new referrals or extended hours';
  setStat('statFreed', '+' + freed);
  setStat('statUtil', (90 - freed) + '%');
  $('#statUtilDelta').textContent = '↓ from 90%';

  setStat('statCapacity', '+' + freed);
  setStat('statNewRev', '+' + money(freed * 540));
  setStat('statPipeline', String(crm.filter(function (c) { return c.action !== 'pitch' && !c.done; }).length + 1));
  $('#netFormula').textContent = freed + ' sessions × $480 avg × 4 weeks';
  setStat('netImpact', '+' + money(freed * 480 * 4) + ' / mo');

  if ($('#tab-chairs').classList.contains('active')) renderWeekGrid();
}

/* ---------- CRM ---------- */
function renderCrm() {
  var tb = $('#crmRows');
  tb.innerHTML = '';
  crm.forEach(function (c) {
    var tr = document.createElement('tr');
    var td1 = document.createElement('td');
    td1.innerHTML = '<b></b>' + (c.sub ? '<div class="td-sub"></div>' : '');
    td1.firstChild.textContent = c.name;
    if (c.sub) td1.lastChild.textContent = c.sub;
    tr.appendChild(td1);
    var td2 = document.createElement('td'); td2.textContent = c.practice; tr.appendChild(td2);
    var td3 = document.createElement('td'); td3.textContent = c.pts; tr.appendChild(td3);
    var td4 = document.createElement('td'); td4.textContent = c.last; tr.appendChild(td4);
    var td5 = document.createElement('td');
    var pill = document.createElement('span');
    pill.className = 'pill ' + c.pillCls + (c.done ? ' swap' : '');
    pill.textContent = c.status;
    td5.appendChild(pill);
    tr.appendChild(td5);
    var td6 = document.createElement('td');
    var btn = document.createElement('button');
    btn.className = 'action-btn' + (c.action === 'pitch' ? ' action-btn-primary' : '');
    btn.textContent = c.done ? '✓ Sent' : c.label;
    btn.disabled = !!c.done;
    btn.addEventListener('click', function () { crmAction(c, btn); });
    td6.appendChild(btn);
    tr.appendChild(td6);
    tb.appendChild(tr);
  });
}

function crmAction(c, btn) {
  if (c.done) return;
  if (c.action === 'pitch') { openPitchModal(); return; }
  if (c.action === 'intro') { openIntroModal(c); return; }
  btn.textContent = 'Sending…';
  btn.disabled = true;
  setTimeout(function () {
    c.done = true;
    c.last = 'Today';
    c.status = c.action === 'email' ? 'Email sent' : 'Update sent';
    c.pillCls = 'p-green';
    renderCrm();
    updateDerived();
    toast((c.action === 'email' ? 'Follow-up email sent to ' : 'Capacity update sent to ') + c.name);
  }, 650);
}

function pitchHtml() {
  var freed = freedSessions();
  return '<ul>' +
    '<li><b>' + freed + ' chair-sessions/week</b> newly available at our facility this cycle.</li>' +
    '<li>Lab-driven incremental dialysis program — ' + shiftedTotal() + ' stable patients safely shifted to 2×/week (Daugirdas 2024 criteria).</li>' +
    '<li>Same-week placement available for new referrals; SNF morning labs reviewed daily.</li>' +
    '<li>Estimated partner revenue: <b>+' + money(freed * 540) + '/week</b> if freed slots are filled at the 3×/wk composite rate.</li>' +
    '</ul><div class="modal-note">Dr. Samuels can refer 8–10 patients. Lead with chair availability and the incremental-dialysis safety data.</div>';
}
function introDraft(c) {
  return 'Subject: Dialysis chair availability — partnership intro\n\n' +
    'Dear ' + c.name + ',\n\n' +
    'I lead partnerships at MedPull Kidney. Our lab-driven incremental dialysis program just freed ' + freedSessions() + ' chair-sessions per week, and we have same-week placement available for new referrals from ' + c.practice + '.\n\n' +
    'We review SNF morning labs daily and adjust schedules using Daugirdas 2024 criteria, so referred patients keep their nephrologist while we handle scheduling and transport coordination.\n\n' +
    'Would you have 15 minutes this week to discuss a referral pathway?\n\n' +
    'Best regards,\nMedPull Kidney — Partnerships';
}

/* ---------- modal ---------- */
function openModal(title, bodyHtml, actions) {
  $('#modalTitle').textContent = title;
  $('#modalBody').innerHTML = bodyHtml;
  var wrap = $('#modalActions');
  wrap.innerHTML = '';
  actions.forEach(function (a) {
    var b = document.createElement('button');
    b.className = a.primary ? 'action-btn action-btn-primary' : 'action-btn';
    b.textContent = a.label;
    b.addEventListener('click', a.fn);
    wrap.appendChild(b);
  });
  $('#modalOverlay').classList.add('show');
}
function closeModal() { $('#modalOverlay').classList.remove('show'); }

function copyText(text, okMsg) {
  var done = function () { toast(okMsg); };
  if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(text).then(done, done);
  else done();
}

function openPitchModal() {
  openPitchModal.body = pitchHtml();
  openModal('Referral pitch — Dr. R. Samuels', openPitchModal.body, [
    { label: 'Copy summary', fn: function () { copyText($('#modalBody').innerText || '', 'Pitch summary copied'); } },
    { label: 'Close', primary: true, fn: closeModal }
  ]);
}
function openIntroModal(c) {
  openModal('Draft intro — ' + c.name, '<textarea id="introDraft"></textarea>', [
    { label: 'Copy draft', fn: function () { copyText($('#introDraft').value, 'Draft copied to clipboard'); } },
    { label: 'Send intro', primary: true, fn: function () {
        c.done = true; c.last = 'Today'; c.status = 'Intro sent'; c.pillCls = 'p-green';
        closeModal(); renderCrm(); updateDerived();
        toast('Intro email sent to ' + c.name);
      } }
  ]);
  $('#introDraft').value = introDraft(c);
}

function openSnfModal() {
  var rows = patients.filter(function (p) { return p.kind !== 'pending'; }).map(function (p) {
    var sched = (p.kind === 'shift' && p.applied) ? '2×/week' : '3×/week';
    return '<li><b>' + p.name + '</b> — ' + sched + '</li>';
  }).join('');
  openModal('Send schedule to SNF', 'This will send the current weekly dialysis schedule to <b>Sunrise Manor SNF</b>:<ul>' + rows + '</ul>', [
    { label: 'Cancel', fn: closeModal },
    { label: 'Send schedule', primary: true, fn: function () {
        closeModal();
        toast('Weekly schedule sent to Sunrise Manor SNF — 5 patients');
      } }
  ]);
}

/* ---------- exports ---------- */
function download(filename, text) {
  var a = document.createElement('a');
  try {
    a.href = URL.createObjectURL(new Blob([text], { type: 'text/csv' }));
  } catch (e) {
    a.href = 'data:text/csv;charset=utf-8,' + encodeURIComponent(text);
  }
  a.download = filename;
  document.body.appendChild(a);
  try { a.click(); } catch (e2) { /* non-browser env */ }
  a.remove();
  toast('Exported ' + filename);
}
function exportSchedule() {
  var lines = ['Patient,Kt/V,BUN,Residual kidney fn,Current schedule,Recommendation,Status'];
  patients.forEach(function (p) {
    var current = (p.kind === 'shift' && p.applied) ? '2x/week' : '3x/week';
    var rec = p.kind === 'shift' ? 'Shift to 2x/week' : p.kind === 'maintain' ? 'Maintain 3x/week' : p.kind === 'escalate' ? 'Consider 4x/week' : 'Awaiting labs';
    var status = p.kind === 'shift' ? (p.applied ? 'Applied' : 'Pending apply') : p.flagged ? 'Flagged for review' : p.held ? 'On hold' : 'No change';
    lines.push('"' + p.name + '",' + p.ktv + ',"' + p.bun + '","' + p.rkf + '",' + current + ',"' + rec + '","' + status + '"');
  });
  download('schedule-changes.csv', lines.join('\n'));
}
function exportBilling() {
  var freed = freedSessions();
  var lines = [
    'Item,Code,Amount',
    '"Hemodialysis composite rate (3x/wk)",CPT 90960,$540 / session',
    '"Hemodialysis composite rate (2x/wk)",CPT 90961,$480 / session',
    '"Nephrologist visit - SNF (attending)",CPT 99307-99310,$120-$210 / visit',
    '"Vascular access management",CPT 36147,$190 / procedure',
    '"Freed chair-sessions per week",,' + freed,
    '"Est. new weekly revenue (slots filled at 3x/wk rate)",,' + money(freed * 540),
    '"Net impact - freed chairs filled (est.)",,"' + money(freed * 480 * 4) + ' / mo"'
  ];
  download('billing-report.csv', lines.join('\n'));
}

/* ---------- init ---------- */
function init() {
  var d = new Date();
  var months = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
  $('#dateBadge').textContent = 'SNF Morning Labs — ' + months[d.getMonth()] + ' ' + d.getDate() + ', ' + d.getFullYear();

  $$('.tab').forEach(function (t) {
    t.addEventListener('click', function () { showTab(t.getAttribute('data-tab')); });
  });
  $$('[data-tab-go]').forEach(function (b) {
    b.addEventListener('click', function () { showTab(b.getAttribute('data-tab-go')); });
  });
  $('#exportSchedule').addEventListener('click', exportSchedule);
  $('#exportBilling').addEventListener('click', exportBilling);
  $('#sendSnf').addEventListener('click', openSnfModal);
  $('#modalX').addEventListener('click', closeModal);
  $('#modalOverlay').addEventListener('click', function (e) { if (e.target === this) closeModal(); });

  renderPatients();
  renderCrm();
  renderWeekGrid();
  updateDerived();
  showTab('labs');
}
if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
else init();
