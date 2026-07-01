/* =============================================================================
 * assets/bus.js — MedPull Ortho demo · cross-surface event bus  (SPEC §3)
 * -----------------------------------------------------------------------------
 * A tiny, dependency-free pub/sub layered over window.postMessage so the four
 * surfaces (patient / tablet / dashboard / demo shell) can talk to each other
 * whether they run standalone or embedded as <iframe>s — and it works over
 * file:// (opaque / "null" origins), so we NEVER inspect event.origin.
 *
 *   Bus.on(type, handler)   subscribe; returns an unsubscribe() fn.
 *   Bus.emit(type, payload) dispatch locally, then fan out to the parent
 *                           window (if framed) and to every <iframe> here.
 *
 * Wire envelope: { __mpbus:true, type, payload }.
 * On RECEIVING an __mpbus message we only dispatch locally — we never re-post
 * it — which is what keeps events from looping forever between frames.
 *
 * Vanilla JS. No frameworks, no CDNs, no build step, no storage, no network.
 * ===========================================================================*/
(function (global) {
  'use strict';

  // The window we live in. Guarded so the file also parses/loads cleanly in a
  // non-browser context (e.g. `node --check`, or an accidental require()).
  var win = (typeof window !== 'undefined') ? window : null;

  // When a surface is embedded in the guided-demo shell, hide its standalone
  // page-hint strip. `window.top !== window.self` is readable even cross-origin
  // / over file://; if it throws, assume framed.
  try {
    if (win && win.self !== win.top && document.documentElement) document.documentElement.classList.add('framed');
  } catch (e) { try { document.documentElement.classList.add('framed'); } catch (e2) {} }

  // Canonical event types (SPEC §3). Exposed as Bus.TYPES for callers that
  // prefer a constant over a bare string; emit()/on() still accept raw strings.
  var TYPES = {
    TASK_ASSIGN:   'TASK_ASSIGN',   // payload: patientId (string)
    RISK_ESCALATE: 'RISK_ESCALATE', // payload: patientId (string)
    SMS_FIRE:      'SMS_FIRE',      // payload: { patient, text }
    CHECKIN:       'CHECKIN',       // payload: { patientId, symptom }
    NAV:           'NAV',           // payload: { surface, patientId }
    DEMO_STEP:     'DEMO_STEP'      // payload: { n }
  };

  // type -> array of handler fns.
  var handlers = Object.create(null);

  /**
   * Subscribe to an event type.
   * @param {string} type    one of TYPES (or any string).
   * @param {Function} handler  called with (payload) when the event fires.
   * @returns {Function} unsubscribe — call it to remove this handler.
   */
  function on(type, handler) {
    if (typeof type !== 'string' || typeof handler !== 'function') return function () {};
    (handlers[type] || (handlers[type] = [])).push(handler);
    return function off() { removeHandler(type, handler); };
  }

  /** Remove a previously-registered handler (no-op if not found). */
  function removeHandler(type, handler) {
    var list = handlers[type];
    if (!list) return;
    var i = list.indexOf(handler);
    if (i !== -1) list.splice(i, 1);
  }

  /**
   * Run every local handler for `type`. One throwing handler must not stop the
   * others, so each call is isolated in its own try/catch.
   */
  function dispatch(type, payload) {
    var list = handlers[type];
    if (!list || !list.length) return;
    // Iterate a copy so handlers can safely on()/off() during dispatch.
    var snapshot = list.slice();
    for (var i = 0; i < snapshot.length; i++) {
      try {
        snapshot[i](payload);
      } catch (err) {
        // Never let a bad subscriber break the bus or sibling handlers.
        if (win && win.console && win.console.error) {
          win.console.error('[Bus] handler for "' + type + '" threw:', err);
        }
      }
    }
  }

  /**
   * post() a wire envelope into another window, defensively. postMessage into a
   * cross-origin / not-yet-loaded frame can throw; swallow it so one bad target
   * never aborts the fan-out to the rest.
   */
  function post(targetWin, envelope) {
    if (!targetWin || typeof targetWin.postMessage !== 'function') return;
    try {
      // Always '*': file:// origins are opaque, so a strict targetOrigin fails.
      targetWin.postMessage(envelope, '*');
    } catch (err) { /* frame gone / cross-origin — ignore */ }
  }

  /**
   * Emit an event: dispatch it locally, then broadcast one hop outward — up to
   * the parent window (if we're framed) and down into every <iframe> in this
   * document. Receivers only re-dispatch locally, so events never loop.
   *
   * @param {string} type    one of TYPES.
   * @param {*} payload       event-specific payload (see TYPES comments).
   */
  function emit(type, payload) {
    if (typeof type !== 'string') return;

    // 1) Local subscribers in THIS window.
    dispatch(type, payload);

    if (!win) return; // non-browser context: local-only, nothing to fan out to.

    var envelope = { __mpbus: true, type: type, payload: payload };

    // 2) Up: notify the parent shell, but only if we're actually embedded
    //    (win.parent === win when we're the top-level document).
    if (win.parent && win.parent !== win) {
      post(win.parent, envelope);
    }

    // 3) Down: notify every child <iframe> surface. Re-query each time so
    //    frames added after load are included.
    var frames = win.document ? win.document.getElementsByTagName('iframe') : [];
    for (var i = 0; i < frames.length; i++) {
      // contentWindow is null until the frame has a browsing context; post()
      // guards that. Accessing .contentWindow cross-origin is allowed.
      post(frames[i].contentWindow, envelope);
    }
  }

  /**
   * Inbound message handler. Only messages we recognise (__mpbus === true) are
   * acted on; everything else on the page's message channel is ignored. We do
   * NOT re-broadcast — receiving dispatches locally only, which prevents loops.
   */
  function onMessage(e) {
    var data = e && e.data;
    // Guard: ignore anything that isn't one of our envelopes. (We never read
    // e.origin — file:// origins are opaque/"null".)
    if (!data || data.__mpbus !== true || typeof data.type !== 'string') return;
    dispatch(data.type, data.payload);
  }

  if (win && typeof win.addEventListener === 'function') {
    win.addEventListener('message', onMessage, false);
  }

  // Public API — small and stable (SPEC §3). `off`, the unsubscribe return, and
  // `TYPES` are additive conveniences; `on`/`emit` are the contract.
  var Bus = {
    on: on,
    off: removeHandler,
    emit: emit,
    TYPES: TYPES
  };

  // Expose globally for <script>-tag usage; also CommonJS-friendly for tests.
  if (global) global.Bus = Bus;
  if (typeof module !== 'undefined' && module.exports) module.exports = Bus;

})(typeof window !== 'undefined' ? window : (typeof globalThis !== 'undefined' ? globalThis : this));
