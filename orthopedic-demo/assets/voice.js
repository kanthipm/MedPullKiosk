/* =============================================================================
 * assets/voice.js — shared, no-backend text-to-speech for the demo.
 * -----------------------------------------------------------------------------
 * The only offline / "just open index.html" TTS available in a static site is
 * the browser Web Speech API (window.speechSynthesis), which uses whatever OS /
 * browser voices are installed. This module picks the BEST available natural
 * FEMALE voice (for the demo narrator + the app's assistant) and MALE voice
 * (for the patient, Marcus), and speaks a little FASTER so it sounds more human
 * and less plodding. Chrome exposes higher-quality "Google" voices; on macOS,
 * installing an "Enhanced" / "Premium" system voice improves quality further.
 *
 *   Voice.speak(text, role)   role ∈ 'narrator' | 'assistant' | 'marcus'
 *   Voice.cancel()            stop / flush the queue
 *   Voice.available()         boolean
 *
 * No frameworks, no network, no build step.
 * ===========================================================================*/
(function () {
  'use strict';

  var TTS = (typeof window !== 'undefined') && ('speechSynthesis' in window) && (typeof SpeechSynthesisUtterance !== 'undefined');
  var picked = { female: null, male: null, ready: false };

  /* preference lists, best-first — natural neural voices before robotic ones */
  var FEMALE = ['Google US English', 'Samantha', 'Ava', 'Allison', 'Susan', 'Zoe', 'Serena', 'Karen', 'Moira', 'Tessa', 'Fiona',
    'Microsoft Aria', 'Microsoft Jenny', 'Microsoft Zira', 'Kathy'];
  var MALE = ['Google UK English Male', 'Daniel', 'Arthur', 'Oliver', 'Tom', 'Aaron', 'Alex', 'Rishi', 'Gordon',
    'Microsoft Guy', 'Microsoft David', 'Reed', 'Fred'];

  function scoreName(name, prefs) {
    var n = String(name || '').toLowerCase();
    for (var i = 0; i < prefs.length; i++) { if (n.indexOf(prefs[i].toLowerCase()) >= 0) return prefs.length - i; }
    return 0;
  }

  function pick() {
    if (!TTS) return;
    var voices = window.speechSynthesis.getVoices() || [];
    if (!voices.length) return;                       /* not ready yet — onvoiceschanged retries */
    var en = voices.filter(function (v) { return /^en(-|_)/i.test(v.lang || '') || /english/i.test(v.name || ''); });
    if (!en.length) en = voices;

    var bestF = null, sf = -1, bestM = null, sm = -1;
    en.forEach(function (v) {
      var name = v.name || '';
      var f = scoreName(name, FEMALE) + (/female/i.test(name) ? 0.5 : 0) + (/^en-US/i.test(v.lang) ? 0.2 : 0);
      var m = scoreName(name, MALE) + ((/male/i.test(name) && !/female/i.test(name)) ? 0.5 : 0);
      if (f > sf) { sf = f; bestF = v; }
      if (m > sm) { sm = m; bestM = v; }
    });
    picked.female = bestF || en[0];
    picked.male = bestM || bestF || en[0];
    picked.ready = true;
  }

  if (TTS) {
    pick();
    /* voices often load asynchronously — re-pick when they arrive */
    try { window.speechSynthesis.onvoiceschanged = pick; } catch (e) {}
  }

  /* per-role delivery: faster than the 1.0 default; small pitch character */
  var ROLE = {
    narrator:  { voice: 'female', rate: 1.14, pitch: 1.0 },
    assistant: { voice: 'female', rate: 1.12, pitch: 1.03 },
    marcus:    { voice: 'male',   rate: 1.06, pitch: 0.92 }
  };

  function speak(text, role) {
    if (!TTS || !text) return;
    try {
      if (!picked.ready) pick();
      var cfg = ROLE[role] || ROLE.narrator;
      var v = picked[cfg.voice] || picked.female || picked.male;
      var u = new SpeechSynthesisUtterance(String(text));
      if (v) u.voice = v;
      u.rate = cfg.rate; u.pitch = cfg.pitch; u.lang = (v && v.lang) || 'en-US';
      window.speechSynthesis.speak(u);          /* utterances QUEUE — callers cancel() to interrupt */
    } catch (e) { /* non-fatal */ }
  }
  function cancel() { try { if (TTS) window.speechSynthesis.cancel(); } catch (e) {} }

  window.Voice = { speak: speak, cancel: cancel, available: function () { return TTS; }, picked: picked };
})();
