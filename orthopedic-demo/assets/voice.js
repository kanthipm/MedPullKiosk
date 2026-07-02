/* =============================================================================
 * assets/voice.js — natural voice playback for the demo.
 * -----------------------------------------------------------------------------
 * Preferred path: pre-generated neural voice clips (Kokoro-82M, generated
 * locally by tools/generate_voices.py) listed in assets/audio/manifest.js as
 * window.VOICE_CLIPS, keyed "<role>|<exact text>". Clips queue back-to-back,
 * matching the old speechSynthesis queueing that callers rely on.
 * Re-run the generator after editing any spoken line.
 *
 * Fallback: browser Web Speech API (speechSynthesis) for any text that has no
 * clip, so ad-hoc lines still speak — just in the older synthetic voice.
 *
 *   Voice.speak(text, role)      role ∈ 'narrator' | 'assistant' | 'marcus'
 *   Voice.cancel()               stop playback / flush the queue
 *   Voice.duration(text, role)   clip length in ms, or null if no clip
 *   Voice.available()            boolean
 *   Voice.setMuted(bool)         mute/unmute (also stops anything in flight)
 *   Voice.isMuted()              boolean
 *   (also listens for Bus 'MUTE' {muted} so all frames mute together)
 *
 * No frameworks, no build step; clips are plain <audio> so file:// works.
 * ===========================================================================*/
(function () {
  'use strict';

  /* ---- pre-generated clips ------------------------------------------------ */
  var cache = {};                        /* clip file -> Audio element */
  var queue = [], playing = null;
  var muted = false;

  function entry(text, role) {
    var m = window.VOICE_CLIPS;
    return (m && m[(role || 'narrator') + '|' + String(text)]) || null;
  }

  function playNext() {
    var next = queue.shift();
    if (!next) { playing = null; return; }
    var a = cache[next.f] || (cache[next.f] = new Audio(next.f));
    playing = a;
    try { a.currentTime = 0; } catch (e) {}
    a.onended = playNext;
    var p = a.play();
    if (p && p.catch) p.catch(function () {   /* autoplay blocked — stay silent */
      /* a.paused distinguishes a real block from a stale AbortError left by a
         cancel()-then-respeak of the same clip (element already playing again) */
      if (playing === a && a.paused) { playing = null; queue = []; }
    });
  }

  function stopClips() {
    queue = [];
    if (playing) { try { playing.onended = null; playing.pause(); } catch (e) {} playing = null; }
  }

  /* ---- speechSynthesis fallback (only for text without a clip) ------------ */
  var TTS = (typeof window !== 'undefined') && ('speechSynthesis' in window) && (typeof SpeechSynthesisUtterance !== 'undefined');
  var picked = { female: null, male: null, ready: false };

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
    try { window.speechSynthesis.onvoiceschanged = pick; } catch (e) {}
  }

  var ROLE = {
    narrator:  { voice: 'female', rate: 1.14, pitch: 1.0 },
    assistant: { voice: 'female', rate: 1.12, pitch: 1.03 },
    marcus:    { voice: 'male',   rate: 1.06, pitch: 0.92 }
  };

  function speakFallback(text, role) {
    if (!TTS) return;
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

  /* ---- public API ---------------------------------------------------------- */
  function speak(text, role) {
    if (!text || muted) return;
    var e = entry(text, role);
    if (e) {
      queue.push(e);
      if (!playing) playNext();
      return;
    }
    speakFallback(text, role);
  }

  function cancel() {
    stopClips();
    try { if (TTS) window.speechSynthesis.cancel(); } catch (e) {}
  }

  function duration(text, role) {
    var e = entry(text, role);
    return e ? e.ms : null;
  }

  function setMuted(m) {
    muted = !!m;
    if (muted) cancel();
  }

  /* mute everywhere at once: the shell broadcasts MUTE over the Bus so every
     framed surface's Voice instance follows suit. */
  if (window.Bus && window.Bus.on) {
    window.Bus.on('MUTE', function (pl) { setMuted(pl && pl.muted); });
  }

  window.Voice = {
    speak: speak,
    cancel: cancel,
    duration: duration,
    setMuted: setMuted,
    isMuted: function () { return muted; },
    available: function () { return !!window.VOICE_CLIPS || TTS; },
    picked: picked
  };
})();
