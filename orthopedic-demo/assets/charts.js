/* ============================================================================
 * MedPull Ortho demo · assets/charts.js
 * Hand-rolled, dependency-free SVG chart toolkit ported VERBATIM (in behaviour)
 * from reference/micro_lab_my_body_phone_app.html (§2 of SPEC.md).
 *
 * Everything lives inside ONE IIFE. Internal helpers stay module-local; the
 * public API is namespaced on window.Charts. Every function returns an SVG
 * STRING sized to fit `.fig` (fills its container width). No DOM, no network.
 *
 * Changes vs. the reference (per build contract):
 *  1. Public members namespaced on window.Charts; helpers module-local.
 *  2. series(rng,n,base,amp,drift,sd) — takes an rng generator as its first arg
 *     (instead of the reference's global R).
 *  3. Every fig* now takes a single opts object:
 *       figX({seed,title,xlabel,ylabel,band,note,calloutText})
 *     all optional, falling back to the reference defaults. The reference
 *     pAxis() default xlabel is replaced by 'Post-op day'. rng(opts.seed||<RA
 *     default seed>) so different seeds yield distinct-but-house-style figures.
 *  4. Charts.render(name,opts) → Charts[name](opts), or a neutral placeholder
 *     SVG if the name is unknown (never throws).
 * ========================================================================== */
(function () {
  'use strict';

  /* Default post-op x-axis label (replaces the reference pAxis()). */
  var PAXIS = 'Post-op day';

  /* ---------------------------------------------------------------- core */
  var _uid = 0;
  var uid = function () { return 'g' + (++_uid); };

  function rng(seed) {
    var x = seed % 2147483647;
    if (x <= 0) x += 2147483646;
    return function () { x = (x * 48271) % 2147483647; return x / 2147483647; };
  }

  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (m) {
      return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' })[m];
    });
  }

  /* Parameterized on an rng GENERATOR (call rng() for the next value). */
  function series(rng, n, base, amp, drift, sd) {
    var a = [];
    for (var i = 0; i < n; i++) a.push(base + Math.sin(i / 2.1) * amp + drift * i + (rng() - 0.5) * sd);
    return a;
  }

  /* ------------------------------------------------------- svg primitives */
  function grad(id, c0, c1, horiz) {
    return '<linearGradient id="' + id + '" x1="0" y1="0" x2="' + (horiz ? 1 : 0) + '" y2="' + (horiz ? 0 : 1) + '"><stop offset="0" stop-color="' + c0 + '"/><stop offset="1" stop-color="' + c1 + '"/></linearGradient>';
  }
  function pol(cx, cy, r, deg) { var a = (deg - 90) * Math.PI / 180; return [cx + r * Math.cos(a), cy + r * Math.sin(a)]; }
  function arc(cx, cy, r, a0, a1) {
    var p0 = pol(cx, cy, r, a0), p1 = pol(cx, cy, r, a1), lg = (a1 - a0) > 180 ? 1 : 0;
    return 'M' + p0[0].toFixed(2) + ' ' + p0[1].toFixed(2) + ' A' + r + ' ' + r + ' 0 ' + lg + ' 1 ' + p1[0].toFixed(2) + ' ' + p1[1].toFixed(2);
  }

  /* ---------------------------------------------- clinical-figure toolkit */
  function cgeo(o) { return Object.assign({ x0: 50, x1: 420, y0: 30, y1: 196, xticks: 5, yticks: 4 }, o); }

  function cframe(g) {
    var X = function (v) { return g.x0 + (v - g.xmin) / (g.xmax - g.xmin) * (g.x1 - g.x0); };
    var Y = function (v) { return g.y1 - (v - g.ymin) / (g.ymax - g.ymin) * (g.y1 - g.y0); };
    var s = '<rect x="' + g.x0 + '" y="' + g.y0 + '" width="' + (g.x1 - g.x0).toFixed(1) + '" height="' + (g.y1 - g.y0).toFixed(1) + '" fill="#fff" stroke="#dbe2ee"/>';
    var xt = g.xticks, yt = g.yticks, i, v, x, y;
    for (i = 0; i <= xt; i++) {
      v = g.xmin + (g.xmax - g.xmin) * i / xt; x = X(v);
      if (g.grid !== false) s += '<line x1="' + x.toFixed(1) + '" y1="' + g.y0 + '" x2="' + x.toFixed(1) + '" y2="' + g.y1 + '" stroke="#eef2f8"/>';
      s += '<text x="' + x.toFixed(1) + '" y="' + (g.y1 + 14) + '" fill="#7c879e" font-size="9" text-anchor="middle">' + (g.xfmt ? g.xfmt(v) : (+v.toFixed(2))) + '</text>';
    }
    for (i = 0; i <= yt; i++) {
      v = g.ymin + (g.ymax - g.ymin) * i / yt; y = Y(v);
      if (g.grid !== false) s += '<line x1="' + g.x0 + '" y1="' + y.toFixed(1) + '" x2="' + g.x1 + '" y2="' + y.toFixed(1) + '" stroke="#eef2f8"/>';
      s += '<text x="' + (g.x0 - 6) + '" y="' + (y + 3).toFixed(1) + '" fill="#7c879e" font-size="9" text-anchor="end">' + (g.yfmt ? g.yfmt(v) : (+v.toFixed(2))) + '</text>';
    }
    if (g.title) s += '<text x="' + g.x0 + '" y="18" fill="#10264f" font-size="12.5" font-weight="800">' + esc(g.title) + '</text>';
    if (g.xlabel) s += '<text x="' + ((g.x0 + g.x1) / 2).toFixed(1) + '" y="' + (g.y1 + 28) + '" fill="#7c879e" font-size="10" text-anchor="middle">' + esc(g.xlabel) + '</text>';
    if (g.ylabel) { var my = ((g.y0 + g.y1) / 2).toFixed(1); s += '<text x="14" y="' + my + '" fill="#7c879e" font-size="10" text-anchor="middle" transform="rotate(-90 14 ' + my + ')">' + esc(g.ylabel) + '</text>'; }
    return { s: s, X: X, Y: Y };
  }

  function cborder(g) { return '<rect x="' + g.x0 + '" y="' + g.y0 + '" width="' + (g.x1 - g.x0).toFixed(1) + '" height="' + (g.y1 - g.y0).toFixed(1) + '" fill="none" stroke="#dbe2ee"/>'; }

  function cpath(d, X, Y, c, w, dash) {
    var p = '';
    for (var i = 0; i < d.length; i++) p += (i ? 'L' : 'M') + X(d[i][0]).toFixed(1) + ' ' + Y(d[i][1]).toFixed(1) + ' ';
    return '<path d="' + p + '" fill="none" stroke="' + c + '" stroke-width="' + (w || 1.5) + '"' + (dash ? ' stroke-dasharray="' + dash + '"' : '') + ' stroke-linejoin="round" stroke-linecap="round"/>';
  }

  function gss(p, mu, s) { return Math.exp(-((p - mu) * (p - mu)) / (2 * s * s)); }

  var VIR = [[68, 1, 84], [59, 82, 139], [33, 145, 140], [94, 201, 98], [253, 231, 37]];
  function vir(t) {
    t = Math.max(0, Math.min(1, t));
    var s = t * (VIR.length - 1), i = Math.min(Math.floor(s), VIR.length - 2), f = s - i, a = VIR[i], b = VIR[i + 1];
    return 'rgb(' + Math.round(a[0] + (b[0] - a[0]) * f) + ',' + Math.round(a[1] + (b[1] - a[1]) * f) + ',' + Math.round(a[2] + (b[2] - a[2]) * f) + ')';
  }

  function svgWrap(inner, cap) {
    return '<svg class="fig" viewBox="0 0 440 244" style="height:188px"><rect width="440" height="244" fill="#fbfdff"/>' + inner + (cap ? '<text x="50" y="238" fill="#9aa6bd" font-size="9">' + esc(cap) + '</text>' : '') + '</svg>';
  }

  /* Highlight callout inside a 440×244 figure. */
  function callout(x, y, text, c) {
    var w = 10 + String(text).length * 5.3;
    return '<g><circle cx="' + x.toFixed(1) + '" cy="' + y.toFixed(1) + '" r="3.6" fill="none" stroke="' + c + '" stroke-width="2"/><line x1="' + x.toFixed(1) + '" y1="' + (y - 4).toFixed(1) + '" x2="' + x.toFixed(1) + '" y2="' + (y - 17).toFixed(1) + '" stroke="' + c + '" stroke-width="1.2"/><rect x="' + (x - 2).toFixed(1) + '" y="' + (y - 32).toFixed(1) + '" width="' + w.toFixed(1) + '" height="15" rx="4" fill="' + c + '"/><text x="' + (x + 3).toFixed(1) + '" y="' + (y - 21).toFixed(1) + '" font-size="9" font-weight="800" fill="#fff">' + esc(text) + '</text></g>';
  }

  /* Optional band annotation: a short right-aligned label in the title row of a
     cgeo-based figure. Rendered only when opts.band is provided (else no-op). */
  function bandLabel(opts, g) {
    if (!opts || opts.band == null) return '';
    return '<text x="' + g.x1.toFixed(1) + '" y="18" text-anchor="end" font-size="10" font-weight="800" fill="#7c879e">' + esc(opts.band) + '</text>';
  }

  /* ============================ simple widgets (verbatim) ============================ */
  function ringChart(items, centerTop, centerBig, centerSub) {
    var cx = 100, cy = 100, defs = '', s = '';
    items.forEach(function (it, i) {
      var r = 84 - i * 21, C = 2 * Math.PI * r, id = uid();
      defs += grad(id, it.c[0], it.c[1], true);
      s += '<circle cx="' + cx + '" cy="' + cy + '" r="' + r + '" fill="none" stroke="#eef1f7" stroke-width="15"/>';
      s += '<circle cx="' + cx + '" cy="' + cy + '" r="' + r + '" fill="none" stroke="url(#' + id + ')" stroke-width="15" stroke-linecap="round" stroke-dasharray="' + (Math.min(1, it.pct) * C).toFixed(1) + ' ' + (C + 4).toFixed(1) + '" transform="rotate(-90 ' + cx + ' ' + cy + ')"/>';
    });
    var txt = '';
    if (centerBig) txt = '<text x="100" y="98" text-anchor="middle" font-size="34" font-weight="900" fill="#0f1830" letter-spacing="-1">' + esc(centerBig) + '</text>' +
      (centerTop ? '<text x="100" y="74" text-anchor="middle" font-size="12" font-weight="800" fill="#8a93a8">' + esc(centerTop) + '</text>' : '') +
      (centerSub ? '<text x="100" y="118" text-anchor="middle" font-size="11" font-weight="800" fill="#8a93a8">' + esc(centerSub) + '</text>' : '');
    return '<svg class="fig" viewBox="0 0 200 200" style="height:178px"><defs>' + defs + '</defs>' + s + txt + '</svg>';
  }

  function gauge(score, label, statusTxt, c0, c1, max) {
    max = max || 100;
    var cx = 100, cy = 104, r = 78, a0 = -135, a1 = 135, sweep = 270, id = uid(), p = Math.max(0, Math.min(1, score / max));
    var s = '<defs>' + grad(id, c0, c1, true) + '</defs>';
    s += '<path d="' + arc(cx, cy, r, a0, a1) + '" fill="none" stroke="#eef1f7" stroke-width="15" stroke-linecap="round"/>';
    s += '<path d="' + arc(cx, cy, r, a0, a0 + sweep * p) + '" fill="none" stroke="url(#' + id + ')" stroke-width="15" stroke-linecap="round"/>';
    var h = pol(cx, cy, r, a0 + sweep * p);
    s += '<circle cx="' + h[0].toFixed(1) + '" cy="' + h[1].toFixed(1) + '" r="7" fill="#fff" stroke="' + c1 + '" stroke-width="3"/>';
    s += '<text x="' + cx + '" y="' + (cy - 2) + '" text-anchor="middle" font-size="42" font-weight="900" fill="#0f1830" letter-spacing="-2">' + score + '</text>';
    if (label) s += '<text x="' + cx + '" y="' + (cy + 22) + '" text-anchor="middle" font-size="12" font-weight="800" fill="#8a93a8" letter-spacing=".5">' + esc(label) + '</text>';
    if (statusTxt) s += '<text x="' + cx + '" y="' + (cy + 50) + '" text-anchor="middle" font-size="14" font-weight="900" fill="' + c1 + '">' + esc(statusTxt) + '</text>';
    return '<svg class="fig" viewBox="0 0 200 196" style="height:172px">' + s + '</svg>';
  }

  function spark(data, w, h, c0, c1, opts) {
    opts = opts || {}; w = w || 130; h = h || 44;
    var mn = Math.min.apply(null, data), mx = Math.max.apply(null, data), pad = (mx - mn) * 0.2 || 1, lo = mn - pad, hi = mx + pad;
    var X = function (i) { return 3 + i / (data.length - 1) * (w - 6); };
    var Y = function (v) { return h - 4 - ((v - lo) / (hi - lo)) * (h - 9); };
    var id = uid(), id2 = uid(), ln = '';
    data.forEach(function (v, i) { ln += (i ? 'L' : 'M') + X(i).toFixed(1) + ' ' + Y(v).toFixed(1) + ' '; });
    var area = 'M' + X(0).toFixed(1) + ' ' + h + ' L' + data.map(function (v, i) { return X(i).toFixed(1) + ' ' + Y(v).toFixed(1); }).join(' L') + ' L' + X(data.length - 1).toFixed(1) + ' ' + h + ' Z';
    var base = '';
    if (opts.baseline != null) { var by = Y(opts.baseline); base = '<line x1="3" y1="' + by.toFixed(1) + '" x2="' + (w - 3) + '" y2="' + by.toFixed(1) + '" stroke="#cdd5e6" stroke-width="1" stroke-dasharray="3 3"/>'; }
    var last = data.length - 1;
    return '<svg viewBox="0 0 ' + w + ' ' + h + '" width="100%" height="' + h + '" preserveAspectRatio="none"><defs>' + grad(id, c0, c1, true) + '<linearGradient id="' + id2 + '" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="' + c1 + '" stop-opacity=".26"/><stop offset="1" stop-color="' + c1 + '" stop-opacity="0"/></linearGradient></defs>' + base + '<path d="' + area + '" fill="url(#' + id2 + ')"/><path d="' + ln + '" fill="none" stroke="url(#' + id + ')" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/><circle cx="' + X(last).toFixed(1) + '" cy="' + Y(data[last]).toFixed(1) + '" r="3.1" fill="#fff" stroke="' + c1 + '" stroke-width="2"/></svg>';
  }

  function barChart(data, labels, hi, c0, c1, unit) {
    var w = 320, h = 130, pad = 20, bw = (w - pad * 2) / data.length, mx = Math.max.apply(null, data) * 1.18;
    var id = uid(), s = '<defs>' + grad(id, c0, c1) + '</defs>';
    data.forEach(function (v, i) {
      var bh = (v / mx) * (h - 34), x = pad + i * bw + bw * 0.18, y = h - 22 - bh, bwi = bw * 0.64, on = i === hi;
      s += '<rect x="' + x.toFixed(1) + '" y="' + y.toFixed(1) + '" width="' + bwi.toFixed(1) + '" height="' + bh.toFixed(1) + '" rx="6" fill="' + (on ? 'url(#' + id + ')' : '#e7ecf6') + '"/>';
      if (on) s += '<text x="' + (x + bwi / 2).toFixed(1) + '" y="' + (y - 6).toFixed(1) + '" text-anchor="middle" font-size="12" font-weight="900" fill="#0f1830">' + (unit ? (v >= 1000 ? (v / 1000).toFixed(1) + 'k' : v) : v) + '</text>';
      s += '<text x="' + (x + bwi / 2).toFixed(1) + '" y="' + (h - 6) + '" text-anchor="middle" font-size="10" font-weight="800" fill="#9aa3b8">' + esc(labels[i]) + '</text>';
    });
    return '<svg class="fig" viewBox="0 0 ' + w + ' ' + h + '" style="height:142px">' + s + '</svg>';
  }

  function donut(segs) {
    var cx = 66, cy = 66, r = 50, sw = 20, tot = segs.reduce(function (a, b) { return a + b.v; }, 0), a0 = -90, s = '';
    segs.forEach(function (g) { var a1 = a0 + g.v / tot * 360; s += '<path d="' + arc(cx, cy, r, a0, a1 - 1) + '" fill="none" stroke="' + g.c + '" stroke-width="' + sw + '" stroke-linecap="round"/>'; a0 = a1; });
    s += '<text x="' + cx + '" y="' + (cy - 2) + '" text-anchor="middle" font-size="20" font-weight="900" fill="#0f1830">' + esc(segs.center || '') + '</text><text x="' + cx + '" y="' + (cy + 15) + '" text-anchor="middle" font-size="9.5" font-weight="800" fill="#8a93a8">' + esc(segs.sub || '') + '</text>';
    return '<svg viewBox="0 0 132 132" width="132" height="132">' + s + '</svg>';
  }

  function calendarHeat(vals) {
    var cols = 7, cell = 15, gap = 5, w = cols * (cell + gap), rows = Math.ceil(vals.length / cols), h = rows * (cell + gap);
    var c = function (t) { var a = [230, 245, 233], b = [12, 157, 87]; return 'rgb(' + a.map(function (x, i) { return Math.round(x + (b[i] - x) * t); }).join(',') + ')'; };
    var s = '';
    vals.forEach(function (v, i) { var x = (i % cols) * (cell + gap), y = Math.floor(i / cols) * (cell + gap); s += '<rect x="' + x + '" y="' + y + '" width="' + cell + '" height="' + cell + '" rx="4" fill="' + c(v) + '"/>'; });
    return '<svg viewBox="0 0 ' + w + ' ' + h + '" width="' + w + '" height="' + h + '">' + s + '</svg>';
  }

  /* ============================ clinical figures (opts-parameterized) ============================
   * opts = { seed, title, xlabel, ylabel, band, note, calloutText } — all optional.
   * ============================================================================================ */

  function figControl(opts) {
    opts = opts || {};
    var r = rng(opts.seed || 31);
    var g = cgeo({ xmin: 0, xmax: 60, ymin: -3, ymax: 3, xticks: 6, title: opts.title || 'Baseline & anomaly windows', xlabel: opts.xlabel || PAXIS, ylabel: opts.ylabel || 'sigma' });
    var ax = cframe(g);
    var s = '<rect x="' + g.x0 + '" y="' + ax.Y(2).toFixed(1) + '" width="' + (g.x1 - g.x0) + '" height="' + (ax.Y(-2) - ax.Y(2)).toFixed(1) + '" fill="#0f9d58" opacity=".05"/>';
    s += '<line x1="' + g.x0 + '" y1="' + ax.Y(0).toFixed(1) + '" x2="' + g.x1 + '" y2="' + ax.Y(0).toFixed(1) + '" stroke="#0f9d58" stroke-width="1.2"/>';
    [2, -2].forEach(function (v) { s += '<line x1="' + g.x0 + '" y1="' + ax.Y(v).toFixed(1) + '" x2="' + g.x1 + '" y2="' + ax.Y(v).toFixed(1) + '" stroke="#dc4437" stroke-dasharray="5 4" stroke-width="1.1"/>'; });
    var pts = [];
    for (var i = 0; i < 60; i++) { var v = (r() - 0.5) * 2.2; if (i > 40 && i < 46) v += (i - 40) * 0.7; if (i === 22) v = 2.6; pts.push([i, Math.max(-3, Math.min(3, v))]); }
    s += cpath(pts, ax.X, ax.Y, '#5f6b80', 1.2);
    var dots = '';
    pts.forEach(function (p) { var o = Math.abs(p[1]) >= 2; dots += '<circle cx="' + ax.X(p[0]).toFixed(1) + '" cy="' + ax.Y(p[1]).toFixed(1) + '" r="' + (o ? 3.2 : 2) + '" fill="' + (o ? '#dc4437' : '#2f80ed') + '"' + (o ? ' stroke="#fff" stroke-width="1"' : '') + '/>'; });
    var hl = callout(ax.X(22), ax.Y(2.6), opts.calloutText || '+2.6 sigma', '#dc4437');
    return svgWrap(ax.s + s + dots + hl + bandLabel(opts, g) + cborder(g), opts.note || 'control chart - personal baseline crossings');
  }

  function figBaseline(opts) {
    opts = opts || {};
    var r = rng(opts.seed || 41);
    var g = cgeo({ xmin: 0, xmax: 60, ymin: 40, ymax: 80, xticks: 6, title: opts.title || 'Baseline ribbon & trend', xlabel: opts.xlabel || PAXIS, ylabel: opts.ylabel || 'value' });
    var ax = cframe(g);
    var base = 58, sd = 4;
    var s = '<rect x="' + g.x0 + '" y="' + ax.Y(base + sd).toFixed(1) + '" width="' + (g.x1 - g.x0) + '" height="' + (ax.Y(base - sd) - ax.Y(base + sd)).toFixed(1) + '" fill="#2f80ed" opacity=".08"/>';
    s += '<line x1="' + g.x0 + '" y1="' + ax.Y(base).toFixed(1) + '" x2="' + g.x1 + '" y2="' + ax.Y(base).toFixed(1) + '" stroke="#2f80ed" stroke-dasharray="5 4"/>';
    var pts = [];
    for (var i = 0; i < 60; i++) { var v = base + Math.sin(i / 9) * 4 + (r() - 0.5) * 3; pts.push([i, Math.max(40, Math.min(80, v))]); }
    s += cpath(pts, ax.X, ax.Y, '#15233f', 1.4);
    var li = pts.length - 1;
    var hl = callout(ax.X(pts[li][0]), ax.Y(pts[li][1]), opts.calloutText || 'within +/-1 sigma', '#2f80ed');
    return svgWrap(ax.s + s + hl + bandLabel(opts, g) + cborder(g), opts.note || 'personal baseline with rolling value');
  }

  function figRegression(opts) {
    opts = opts || {};
    var r = rng(opts.seed || 23);
    var g = cgeo({ xmin: 0, xmax: 10, ymin: 0, ymax: 10, xticks: 5, title: opts.title || 'Response model & residuals', xlabel: opts.xlabel || 'x', ylabel: opts.ylabel || 'y' });
    var ax = cframe(g);
    var sl = 0.7, ic = 1.6, pts = [], i;
    for (i = 0; i < 50; i++) { var x = r() * 10, y = ic + sl * x + (r() - 0.5) * 2.4; pts.push([x, Math.max(0, Math.min(10, y))]); }
    var fit = [];
    for (var fx = 0; fx <= 10; fx += 0.5) fit.push([fx, ic + sl * fx]);
    var up = fit.map(function (p) { return [p[0], Math.min(10, p[1] + 1)]; });
    var lo = fit.map(function (p) { return [p[0], Math.max(0, p[1] - 1)]; });
    var bp = '<path d="M' + up.map(function (p) { return ax.X(p[0]).toFixed(1) + ' ' + ax.Y(p[1]).toFixed(1); }).join(' L') + ' L' + lo.slice().reverse().map(function (p) { return ax.X(p[0]).toFixed(1) + ' ' + ax.Y(p[1]).toFixed(1); }).join(' L') + ' Z" fill="#0f9d58" opacity=".1"/>';
    var dots = '';
    pts.forEach(function (p) { dots += '<circle cx="' + ax.X(p[0]).toFixed(1) + '" cy="' + ax.Y(p[1]).toFixed(1) + '" r="2.2" fill="#2f80ed" opacity=".55"/>'; });
    var r2 = (0.86 + r() * 0.1).toFixed(2);
    var hl = callout(ax.X(8.4), ax.Y(ic + sl * 8.4), opts.calloutText || ('R2 ' + r2), '#0f9d58');
    return svgWrap(ax.s + bp + cpath(fit, ax.X, ax.Y, '#0f9d58', 2) + dots + hl + bandLabel(opts, g) + cborder(g), opts.note || 'fit + 95% confidence band');
  }

  function figIMU(opts) {
    opts = opts || {};
    var r = rng(opts.seed || 37);
    var g = cgeo({ xmin: 0, xmax: 10, ymin: -2, ymax: 2.6, xticks: 5, title: opts.title || 'Tri-axial motion & bouts', xlabel: opts.xlabel || 'Time (s)', ylabel: opts.ylabel || 'g' });
    var ax = cframe(g);
    var s = '<rect x="' + ax.X(3.5).toFixed(1) + '" y="' + g.y0 + '" width="' + (ax.X(6.8) - ax.X(3.5)).toFixed(1) + '" height="' + (g.y1 - g.y0) + '" fill="#f57c00" opacity=".08"/>';
    var xa = [], ya = [], za = [];
    for (var t = 0; t <= 10; t += 0.04) { var ac = (t > 3.5 && t < 6.8) ? 1 : 0.18; xa.push([t, 0.9 + ac * Math.sin(t * 9) * 0.7 + (r() - 0.5) * 0.1 * ac]); ya.push([t, ac * Math.sin(t * 9 + 2) * 0.6 + (r() - 0.5) * 0.1 * ac]); za.push([t, -1 + ac * Math.sin(t * 9 + 4) * 0.5]); }
    s += cpath(xa, ax.X, ax.Y, '#2f80ed', 1.1) + cpath(ya, ax.X, ax.Y, '#0f9d58', 1.1) + cpath(za, ax.X, ax.Y, '#f57c00', 1.1);
    var hl = callout(ax.X(5.1), ax.Y(1.55), opts.calloutText || 'active bout', '#e07b00');
    return svgWrap(ax.s + s + hl + bandLabel(opts, g) + cborder(g), opts.note || 'accelerometer norm - active bout shaded');
  }

  function figDriver(opts) {
    opts = opts || {};
    var labs = ['HR', 'HRV', 'Sleep', 'SpO2', 'Temp', 'Act'], n = labs.length, r = rng(opts.seed || 47);
    var W = 300, L = 70, T = 44, cell = (W - L - 10) / n;
    var div = function (v) {
      if (v >= 0) return 'rgb(' + Math.round(255 - v * 210) + ',' + Math.round(255 - v * 120) + ',' + Math.round(255 - v * 40) + ')';
      var a = -v; return 'rgb(' + Math.round(255 - a * 40) + ',' + Math.round(255 - a * 120) + ',' + Math.round(255 - a * 210) + ')';
    };
    var M = [], best = 0, bi = 0, bj = 1, i, j;
    for (i = 0; i < n; i++) { M[i] = []; for (j = 0; j < n; j++) { M[i][j] = i === j ? 1 : (M[j] && M[j][i] !== undefined ? M[j][i] : (r() * 1.7 - 0.7)); if (i < j && Math.abs(M[i][j]) > best) { best = Math.abs(M[i][j]); bi = i; bj = j; } } }
    var s = '<text x="10" y="18" fill="#10264f" font-size="12.5" font-weight="800">' + esc(opts.title || 'Driver correlation matrix') + '</text>';
    if (opts.band != null) s += '<text x="' + (W - 10) + '" y="18" text-anchor="end" font-size="10" font-weight="800" fill="#7c879e">' + esc(opts.band) + '</text>';
    for (i = 0; i < n; i++) for (j = 0; j < n; j++) { var x = L + j * cell, y = T + i * cell; s += '<rect x="' + x.toFixed(1) + '" y="' + y.toFixed(1) + '" width="' + (cell - 1.5).toFixed(1) + '" height="' + (cell - 1.5).toFixed(1) + '" rx="3" fill="' + div(M[i][j]) + '"/><text x="' + (x + cell / 2).toFixed(1) + '" y="' + (y + cell / 2 + 3).toFixed(1) + '" text-anchor="middle" font-size="8.5" font-weight="700" fill="' + (Math.abs(M[i][j]) > 0.6 ? '#fff' : '#5f6b80') + '">' + M[i][j].toFixed(1) + '</text>'; }
    labs.forEach(function (l, k) { s += '<text x="' + (L - 6) + '" y="' + (T + k * cell + cell / 2 + 3).toFixed(1) + '" text-anchor="end" font-size="9" font-weight="700" fill="#5f6b80">' + l + '</text><text x="' + (L + k * cell + cell / 2).toFixed(1) + '" y="' + (T - 6) + '" text-anchor="middle" font-size="9" font-weight="700" fill="#5f6b80">' + l + '</text>'; });
    s += '<rect x="' + (L + bj * cell - 1).toFixed(1) + '" y="' + (T + bi * cell - 1).toFixed(1) + '" width="' + (cell + 0.5).toFixed(1) + '" height="' + (cell + 0.5).toFixed(1) + '" rx="3" fill="none" stroke="#0b1b3a" stroke-width="2"/>';
    var H = T + n * cell + 22;
    return '<svg class="fig" viewBox="0 0 ' + W + ' ' + H + '" style="height:188px"><rect width="' + W + '" height="' + H + '" fill="#fbfdff"/>' + s + '<text x="10" y="' + (H - 7) + '" fill="#9aa6bd" font-size="9">' + esc(opts.note || ('strongest driver ' + labs[bi] + '-' + labs[bj] + ' r=' + best.toFixed(2))) + '</text></svg>';
  }

  function figStacked(opts) {
    opts = opts || {};
    var r = rng(opts.seed || 53), labs = [['Sleep', '#6c5ce7'], ['Cardiac', '#ff4d6d'], ['Activity', '#1fd17a']];
    var W = 320, H = 178, L = 54, T = 26, laneH = (H - T - 22) / 3;
    var s = '<text x="10" y="18" fill="#10264f" font-size="12.5" font-weight="800">' + esc(opts.title || 'Connected timelines') + '</text>';
    var ex0 = L + (W - L - 8) * 0.55, ex1 = L + (W - L - 8) * 0.68;
    s += '<rect x="' + ex0.toFixed(1) + '" y="' + (T - 2) + '" width="' + (ex1 - ex0).toFixed(1) + '" height="' + (laneH * 3).toFixed(1) + '" fill="#ff9f1c" opacity=".14"/>';
    labs.forEach(function (l, li) {
      var y0 = T + li * laneH, yc = y0 + laneH / 2, data = [], i;
      for (i = 0; i < 40; i++) { var v = Math.sin(i / 5 + li) * 0.6 + (r() - 0.5) * 0.3; if (i > 22 && i < 28) v += (li === 1 ? 1.2 : -0.8); data.push(v); }
      var X = function (k) { return L + k / 39 * (W - L - 8); }, Y = function (v) { return yc - v * (laneH * 0.38); }, ln = '';
      data.forEach(function (v, k) { ln += (k ? 'L' : 'M') + X(k).toFixed(1) + ' ' + Y(v).toFixed(1) + ' '; });
      s += '<line x1="' + L + '" y1="' + (y0 + laneH - 2).toFixed(1) + '" x2="' + (W - 8) + '" y2="' + (y0 + laneH - 2).toFixed(1) + '" stroke="#eef2f8"/><text x="' + (L - 6) + '" y="' + (yc + 3).toFixed(1) + '" text-anchor="end" font-size="9" font-weight="800" fill="' + l[1] + '">' + l[0] + '</text><path d="' + ln + '" fill="none" stroke="' + l[1] + '" stroke-width="1.8" stroke-linejoin="round"/>';
    });
    s += '<text x="' + ((ex0 + ex1) / 2).toFixed(1) + '" y="' + (T - 6) + '" text-anchor="middle" font-size="8.5" font-weight="800" fill="#e08600">' + esc(opts.band || 'shared event') + '</text>';
    return '<svg class="fig" viewBox="0 0 ' + W + ' ' + H + '" style="height:178px"><rect width="' + W + '" height="' + H + '" fill="#fbfdff"/>' + s + '<text x="10" y="' + (H - 6) + '" fill="#9aa6bd" font-size="9">' + esc(opts.note || 'profiles moving together - lagged drivers') + '</text></svg>';
  }

  function figPoincare(opts) {
    opts = opts || {};
    var r = rng(opts.seed || 11), N = 120, rr = [], c = 820, i;
    for (i = 0; i < N; i++) { c += (r() - 0.5) * 60; c = Math.max(660, Math.min(990, c)); rr.push(c + Math.sin(i / 6) * 16); }
    var mn = Math.min.apply(null, rr) - 30, mx = Math.max.apply(null, rr) + 30;
    var g = cgeo({ xmin: mn, xmax: mx, ymin: mn, ymax: mx, xticks: 4, title: opts.title || 'HRV · Poincaré plot', xlabel: opts.xlabel || 'RRn (ms)', ylabel: opts.ylabel || 'RRn+1 (ms)' });
    var ax = cframe(g);
    var s = '<line x1="' + ax.X(mn).toFixed(1) + '" y1="' + ax.Y(mn).toFixed(1) + '" x2="' + ax.X(mx).toFixed(1) + '" y2="' + ax.Y(mx).toFixed(1) + '" stroke="#9aa7bd" stroke-dasharray="4 4"/>';
    var mean = rr.reduce(function (a, b) { return a + b; }, 0) / N, diff = [], sum = [];
    for (i = 0; i < N - 1; i++) { diff.push((rr[i + 1] - rr[i]) / Math.SQRT2); sum.push((rr[i + 1] + rr[i]) / Math.SQRT2); }
    var sd = function (a) { var m = a.reduce(function (x, y) { return x + y; }, 0) / a.length; return Math.sqrt(a.reduce(function (x, y) { return x + (y - m) * (y - m); }, 0) / a.length); };
    var SD1 = sd(diff), SD2 = sd(sum);
    var cx = ax.X(mean), cy = ax.Y(mean), sc = (ax.X(mx) - ax.X(mn)) / (mx - mn);
    s += '<ellipse cx="' + cx.toFixed(1) + '" cy="' + cy.toFixed(1) + '" rx="' + (SD2 * sc).toFixed(1) + '" ry="' + (SD1 * sc).toFixed(1) + '" fill="rgba(108,92,231,.1)" stroke="#6c5ce7" stroke-width="1.6" transform="rotate(-45 ' + cx.toFixed(1) + ' ' + cy.toFixed(1) + ')"/>';
    var dots = '';
    for (i = 0; i < N - 1; i++) dots += '<circle cx="' + ax.X(rr[i]).toFixed(1) + '" cy="' + ax.Y(rr[i + 1]).toFixed(1) + '" r="2" fill="#2f80ed" opacity=".6"/>';
    var box = '<rect x="' + (g.x0 + 8) + '" y="' + (g.y0 + 8) + '" width="92" height="34" rx="5" fill="#fff" stroke="#dce4f0"/><text x="' + (g.x0 + 14) + '" y="' + (g.y0 + 22) + '" font-size="9.5" fill="#5f6368">SD1 ' + SD1.toFixed(0) + ' ms</text><text x="' + (g.x0 + 14) + '" y="' + (g.y0 + 35) + '" font-size="9.5" fill="#5f6368">SD2 ' + SD2.toFixed(0) + ' ms</text>';
    return svgWrap(ax.s + s + dots + box + bandLabel(opts, g) + cborder(g), opts.note || 'beat-to-beat variability · parasympathetic tone');
  }

  function figPSD(opts) {
    opts = opts || {};
    var r = rng(opts.seed || 13);
    var g = cgeo({ xmin: 0, xmax: 0.5, ymin: 0, ymax: 1, xticks: 5, title: opts.title || 'HRV power spectrum', xlabel: opts.xlabel || 'Frequency (Hz)', ylabel: opts.ylabel || 'PSD (norm.)' });
    var ax = cframe(g);
    var band = function (a, b, c) { return '<rect x="' + ax.X(a).toFixed(1) + '" y="' + g.y0 + '" width="' + (ax.X(b) - ax.X(a)).toFixed(1) + '" height="' + (g.y1 - g.y0) + '" fill="' + c + '" opacity=".12"/>'; };
    var s = band(0, 0.04, '#9aa7bd') + band(0.04, 0.15, '#2f80ed') + band(0.15, 0.4, '#0f9d58');
    s += '<text x="' + ax.X(0.095).toFixed(1) + '" y="' + (g.y0 + 12) + '" font-size="9" fill="#2f80ed" text-anchor="middle" font-weight="700">LF</text><text x="' + ax.X(0.27).toFixed(1) + '" y="' + (g.y0 + 12) + '" font-size="9" fill="#0f9d58" text-anchor="middle" font-weight="700">HF</text>';
    var lf = 0.09, hf = 0.26, Alf = 0.78, Ahf = 0.62, pts = [];
    for (var f = 0; f <= 0.5; f += 0.004) { var y = Alf / (1 + Math.pow((f - lf) / 0.018, 2)) + Ahf / (1 + Math.pow((f - hf) / 0.03, 2)) + 0.15 / (1 + Math.pow((f - 0.01) / 0.02, 2)) + 0.02; y += r() * 0.012; pts.push([f, Math.min(y, 1)]); }
    var noteBox = '<rect x="' + (g.x1 - 94) + '" y="' + (g.y0 + 6) + '" width="88" height="20" rx="5" fill="#fff" stroke="#dce4f0"/><text x="' + (g.x1 - 50) + '" y="' + (g.y0 + 20) + '" text-anchor="middle" font-size="10" font-weight="800" fill="#10264f">LF/HF ' + (Alf / Ahf).toFixed(2) + '</text>';
    return svgWrap(ax.s + s + cpath(pts, ax.X, ax.Y, '#15233f', 1.6) + noteBox + cborder(g), opts.note || 'Welch periodogram · autonomic balance');
  }

  function figScalogram(opts) {
    opts = opts || {};
    var r = rng(opts.seed || 5);
    var g = cgeo({ x1: 392, xmin: 0, xmax: 8, ymin: 0, ymax: 1, xticks: 4, yticks: 6, title: opts.title || 'Time-frequency scalogram', xlabel: opts.xlabel || 'Time (s)', ylabel: opts.ylabel || 'Freq (Hz)' });
    var ax = cframe(Object.assign({}, g, { grid: false, yfmt: function (v) { return ['0.5', '1', '2', '4', '8', '16', '32'][Math.round(v * 6)]; } }));
    var nx = 40, ny = 24, cw = (g.x1 - g.x0) / nx, ch = (g.y1 - g.y0) / ny, cells = '', vmax = 1e-6, G = [], i, j;
    for (j = 0; j < ny; j++) { G[j] = []; for (i = 0; i < nx; i++) { var t = i / nx, ff = j / ny, ridge = 0.25 + 0.5 * t + 0.12 * Math.sin(t * 7); var p = Math.exp(-Math.pow(ff - ridge, 2) / (2 * 0.012)) * (0.6 + 0.4 * Math.sin(t * 9)); p += 0.25 * Math.exp(-Math.pow(ff - 0.2, 2) / (2 * 0.02)) * Math.max(0, Math.sin(t * 5)); p += 0.05 * r(); G[j][i] = p; if (p > vmax) vmax = p; } }
    for (j = 0; j < ny; j++) for (i = 0; i < nx; i++) { var x = g.x0 + i * cw, y = g.y0 + (ny - 1 - j) * ch; cells += '<rect x="' + x.toFixed(1) + '" y="' + y.toFixed(1) + '" width="' + (cw + 0.6).toFixed(1) + '" height="' + (ch + 0.6).toFixed(1) + '" fill="' + vir(G[j][i] / vmax) + '"/>'; }
    var cbid = uid(), st = '';
    for (i = 0; i <= 10; i++) st += '<stop offset="' + (i * 10) + '%" stop-color="' + vir(i / 10) + '"/>';
    var cb = '<defs><linearGradient id="' + cbid + '" x1="0" y1="1" x2="0" y2="0">' + st + '</linearGradient></defs><rect x="400" y="' + g.y0 + '" width="12" height="' + (g.y1 - g.y0) + '" fill="url(#' + cbid + ')" stroke="#c9d4e6"/><text x="416" y="' + (g.y0 + 8) + '" font-size="8" fill="#7c879e">hi</text><text x="416" y="' + g.y1 + '" font-size="8" fill="#7c879e">lo</text>';
    return svgWrap(ax.s + cells + cb + bandLabel(opts, g) + cborder(g), opts.note || 'continuous wavelet transform of pulse signal');
  }

  /* ============================ ortho analytics figures ============================ *
   * Light house-style SVG figures inspired by the RA clinician-console lenses
   * (acute:chronic load band, gait-symmetry recovery, cadence↔HR decoupling,
   * walking-bout distribution, circadian cosinor, multivariate composite T²).
   * Same conventions as the clinical figs: figX(opts) → SVG string, opts
   * {seed,title,xlabel,ylabel,note,calloutText,band,plateau,threshold}. */
  function figLoadBand(opts) {
    opts = opts || {}; var r = rng(opts.seed || 71);
    var g = cgeo({ xmin: 0, xmax: 30, ymin: 0.4, ymax: 1.9, xticks: 6, yticks: 5, title: opts.title || 'Acute:chronic load ratio', xlabel: opts.xlabel || 'Post-op day', ylabel: 'ratio' });
    var ax = cframe(g);
    var s = '<rect x="' + g.x0 + '" y="' + ax.Y(1.3).toFixed(1) + '" width="' + (g.x1 - g.x0) + '" height="' + (ax.Y(0.8) - ax.Y(1.3)).toFixed(1) + '" fill="#0f9d58" opacity=".06"/>';
    s += '<line x1="' + g.x0 + '" y1="' + ax.Y(0.8).toFixed(1) + '" x2="' + g.x1 + '" y2="' + ax.Y(0.8).toFixed(1) + '" stroke="#e07b00" stroke-dasharray="5 4" stroke-width="1.1"/>';
    s += '<line x1="' + g.x0 + '" y1="' + ax.Y(1.3).toFixed(1) + '" x2="' + g.x1 + '" y2="' + ax.Y(1.3).toFixed(1) + '" stroke="#dc4437" stroke-dasharray="5 4" stroke-width="1.1"/>';
    var pts = [], ratio = 1.0, peak = 0, pi = 0, i;
    for (i = 0; i < 30; i++) { ratio += (r() - 0.5) * 0.18; if (i === 6) ratio = 1.52; ratio = Math.max(0.5, Math.min(1.85, ratio)); pts.push([i, ratio]); if (ratio > peak) { peak = ratio; pi = i; } }
    s += cpath(pts, ax.X, ax.Y, '#15233f', 1.6);
    var dots = ''; pts.forEach(function (p) { var o = p[1] > 1.3 || p[1] < 0.8; dots += '<circle cx="' + ax.X(p[0]).toFixed(1) + '" cy="' + ax.Y(p[1]).toFixed(1) + '" r="' + (o ? 3 : 1.8) + '" fill="' + (o ? '#dc4437' : '#2f80ed') + '"' + (o ? ' stroke="#fff" stroke-width="1"' : '') + '/>'; });
    var hl = callout(ax.X(pi), ax.Y(peak), opts.calloutText || 'overreach', peak > 1.3 ? '#dc4437' : '#e07b00');
    return svgWrap(ax.s + s + dots + hl + bandLabel(opts, g) + cborder(g), opts.note || 'personalized floor / ceiling · overexertion guard');
  }

  function figSymmetry(opts) {
    opts = opts || {}; var r = rng(opts.seed || 73);
    var g = cgeo({ xmin: 0, xmax: 60, ymin: 0, ymax: 28, xticks: 6, yticks: 4, title: opts.title || 'Gait symmetry recovery', xlabel: opts.xlabel || 'Post-op day', ylabel: 'asymmetry %' });
    var ax = cframe(g);
    var s = '<line x1="' + g.x0 + '" y1="' + ax.Y(5).toFixed(1) + '" x2="' + g.x1 + '" y2="' + ax.Y(5).toFixed(1) + '" stroke="#0f9d58" stroke-dasharray="5 4"/>';
    s += '<text x="' + (g.x1 - 6) + '" y="' + (ax.Y(5) - 4).toFixed(1) + '" text-anchor="end" font-size="9" fill="#0f9d58">target</text>';
    var pts = [], asym = 24 + r() * 4, i, dec;
    for (i = 0; i < 60; i++) { dec = (i < 38 || !opts.plateau) ? (0.55 + r() * 0.15) : 0.04; asym = Math.max(opts.plateau ? 9 : 2, asym - dec + (r() - 0.5) * 1.1); pts.push([i, asym]); }
    s += cpath(pts, ax.X, ax.Y, '#6c5ce7', 1.7);
    var li = pts.length - 1, hl = callout(ax.X(pts[li][0]), ax.Y(pts[li][1]), opts.calloutText || (opts.plateau ? 'plateau' : 'improving'), opts.plateau ? '#e07b00' : '#0f9d58');
    return svgWrap(ax.s + s + hl + bandLabel(opts, g) + cborder(g), opts.note || 'stride asymmetry vs. days since surgery');
  }

  function figScatter(opts) {
    opts = opts || {}; var r = rng(opts.seed || 79);
    var g = cgeo({ xmin: 80, xmax: 120, ymin: 90, ymax: 150, xticks: 5, yticks: 4, title: opts.title || 'Cadence ↔ HR decoupling', xlabel: opts.xlabel || 'cadence (spm)', ylabel: 'HR (bpm)' });
    var ax = cframe(g); var sl = opts.slope || 0.55, ic = 90, dots = '', i, cad, hr;
    for (i = 0; i < 44; i++) { cad = 82 + r() * 36; hr = ic + cad * sl + (r() - 0.5) * 14; dots += '<circle cx="' + ax.X(cad).toFixed(1) + '" cy="' + ax.Y(Math.max(90, Math.min(150, hr))).toFixed(1) + '" r="2.4" fill="#2f80ed" opacity=".6"/>'; }
    var band = [[80, ic + 80 * sl], [120, ic + 120 * sl]];
    return svgWrap(ax.s + dots + cpath(band, ax.X, ax.Y, '#0f9d58', 2, '6 4') + bandLabel(opts, g) + cborder(g), opts.note || 'walking-bout cadence vs. heart-rate cost');
  }

  function figHist(opts) {
    opts = opts || {}; var r = rng(opts.seed || 83);
    var bins = ['<1m', '1-3m', '3-6m', '6-10m', '10-20m', '>20m'];
    var vals = bins.map(function (b, i) { return Math.round(6 + r() * 22 * Math.exp(-i * 0.32)); });
    var W = 440, H = 244, L = 50, T = 30, B = 40, mx = Math.max.apply(null, vals) * 1.15;
    var s = '<text x="' + L + '" y="18" fill="#10264f" font-size="12.5" font-weight="800">' + esc(opts.title || 'Walking-bout length distribution') + '</text>';
    var bw = (W - L - 16) / vals.length;
    vals.forEach(function (v, i) {
      var bh = (v / mx) * (H - T - B), x = L + i * bw + bw * 0.16, y = H - B - bh, wi = bw * 0.68;
      s += '<rect x="' + x.toFixed(1) + '" y="' + y.toFixed(1) + '" width="' + wi.toFixed(1) + '" height="' + bh.toFixed(1) + '" rx="4" fill="#2f80ed"/>';
      s += '<text x="' + (x + wi / 2).toFixed(1) + '" y="' + (y - 5).toFixed(1) + '" text-anchor="middle" font-size="9.5" font-weight="800" fill="#10264f">' + v + '</text>';
      s += '<text x="' + (x + wi / 2).toFixed(1) + '" y="' + (H - B + 14) + '" text-anchor="middle" font-size="9" fill="#7c879e">' + esc(bins[i]) + '</text>';
    });
    return '<svg class="fig" viewBox="0 0 ' + W + ' ' + H + '" style="height:188px"><rect width="' + W + '" height="' + H + '" fill="#fbfdff"/>' + s + '<text x="' + L + '" y="' + (H - 8) + '" fill="#9aa6bd" font-size="9">' + esc(opts.note || 'endurance & fragmentation') + '</text></svg>';
  }

  function figCircadian(opts) {
    opts = opts || {}; var r = rng(opts.seed || 89);
    var g = cgeo({ xmin: 0, xmax: 23, ymin: 0, ymax: 90, xticks: 6, yticks: 4, title: opts.title || 'Circadian rest–activity', xlabel: opts.xlabel || 'hour of day', ylabel: 'activity' });
    var ax = cframe(g), mesor = 42, amp = 30 + r() * 8, acro = 15 + r() * 2, s = '', fit = [], h, f, act, bh;
    for (h = 0; h < 24; h++) { f = mesor + amp * Math.cos((2 * Math.PI * (h - acro)) / 24); act = Math.max(0, f + (r() - 0.5) * 16); fit.push([h, Math.max(0, f)]); bh = (act / 90) * (g.y1 - g.y0); s += '<rect x="' + (ax.X(h) - 4).toFixed(1) + '" y="' + (g.y1 - bh).toFixed(1) + '" width="8" height="' + bh.toFixed(1) + '" fill="#2f80ed" opacity=".28"/>'; }
    s += cpath(fit, ax.X, ax.Y, '#e07b00', 1.8);
    return svgWrap(ax.s + s + bandLabel(opts, g) + cborder(g), opts.note || 'cosinor fit · MESOR / amplitude / acrophase');
  }

  function figComposite(opts) {
    opts = opts || {}; var r = rng(opts.seed || 97);
    var g = cgeo({ xmin: 0, xmax: 30, ymin: 0, ymax: 10, xticks: 6, yticks: 5, title: opts.title || 'Multi-signal deviation index', xlabel: opts.xlabel || 'Post-op day', ylabel: 'T² dist' });
    var ax = cframe(g), thr = opts.threshold || 6.5;
    var s = '<line x1="' + g.x0 + '" y1="' + ax.Y(thr).toFixed(1) + '" x2="' + g.x1 + '" y2="' + ax.Y(thr).toFixed(1) + '" stroke="#dc4437" stroke-dasharray="5 4"/>';
    s += '<text x="' + (g.x1 - 6) + '" y="' + (ax.Y(thr) - 4).toFixed(1) + '" text-anchor="end" font-size="9" fill="#dc4437">review threshold</text>';
    var pts = [], base = 2.2, peak = 0, pi = 0, i, v;
    for (i = 0; i < 30; i++) { base += (r() - 0.5) * 0.5; base = Math.max(0.6, base); v = Math.min(9.6, base + (i >= 22 ? (i - 22) * 0.9 : 0)); pts.push([i, v]); if (v > peak) { peak = v; pi = i; } }
    s += cpath(pts, ax.X, ax.Y, '#6c5ce7', 1.8);
    var dots = ''; pts.forEach(function (p) { if (p[1] >= thr) dots += '<circle cx="' + ax.X(p[0]).toFixed(1) + '" cy="' + ax.Y(p[1]).toFixed(1) + '" r="3" fill="#dc4437" stroke="#fff" stroke-width="1"/>'; });
    var hl = callout(ax.X(pi), ax.Y(peak), opts.calloutText || 'signals deviating', peak >= thr ? '#dc4437' : '#6c5ce7');
    return svgWrap(ax.s + s + dots + hl + bandLabel(opts, g) + cborder(g), opts.note || 'Hotelling T² / Mahalanobis vs. personal baseline');
  }

  /* ============================ neutral placeholder + dispatcher ============================ */
  function placeholder(name) {
    return '<svg class="fig" viewBox="0 0 440 160" style="height:150px"><rect x="1" y="1" width="438" height="158" rx="14" fill="#f5f7fc" stroke="#e6ebf5"/><circle cx="220" cy="66" r="17" fill="none" stroke="#c6cfe0" stroke-width="2.4"/><line x1="220" y1="58" x2="220" y2="68" stroke="#c6cfe0" stroke-width="2.4" stroke-linecap="round"/><circle cx="220" cy="74" r="1.6" fill="#c6cfe0"/><text x="220" y="104" text-anchor="middle" font-size="12.5" font-weight="800" fill="#9aa6bd">Figure unavailable</text>' + (name ? '<text x="220" y="122" text-anchor="middle" font-size="10" font-weight="600" fill="#b3bccd">' + esc(name) + '</text>' : '') + '</svg>';
  }

  function render(name, opts) {
    try {
      if (name && name !== 'render' && typeof Charts[name] === 'function') return Charts[name](opts);
    } catch (e) { /* fall through to placeholder — never throw */ }
    return placeholder(name);
  }

  /* ============================ public API (window.Charts) ============================ */
  var Charts = {
    /* core helpers */
    rng: rng, esc: esc, uid: uid, series: series,
    grad: grad, pol: pol, arc: arc,
    cgeo: cgeo, cframe: cframe, cborder: cborder, cpath: cpath,
    gss: gss, VIR: VIR, vir: vir, svgWrap: svgWrap, callout: callout,
    /* simple widgets */
    gauge: gauge, spark: spark, barChart: barChart, ringChart: ringChart, donut: donut, calendarHeat: calendarHeat,
    /* clinical figures */
    figControl: figControl, figBaseline: figBaseline, figRegression: figRegression,
    figIMU: figIMU, figDriver: figDriver, figStacked: figStacked,
    figPoincare: figPoincare, figPSD: figPSD, figScalogram: figScalogram,
    /* ortho analytics figures (RA-console inspired) */
    figLoadBand: figLoadBand, figSymmetry: figSymmetry, figScatter: figScatter,
    figHist: figHist, figCircadian: figCircadian, figComposite: figComposite,
    /* dispatcher */
    render: render
  };

  window.Charts = Charts;
})();
