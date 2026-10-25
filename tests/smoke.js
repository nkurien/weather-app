// Headless smoke test for weather/index.html using jsdom and synthetic Open-Meteo data.
const fs = require('fs');
const { JSDOM, VirtualConsole } = require('jsdom');

const HTML = fs.readFileSync(require('path').join(__dirname, '..', 'index.html'), 'utf8');
const reduceMotion = process.argv.includes('--reduce');

// ---- synthetic forecast: today has 25 hours (clocks go back), then 6 normal days
function pad(n) { return String(n).padStart(2, '0'); }
const times = [];
const dayKeys = ['2026-10-25', '2026-10-26', '2026-10-27', '2026-10-28', '2026-10-29', '2026-10-30', '2026-10-31'];
dayKeys.forEach((k, di) => {
  const n = di === 0 ? 25 : 24;
  for (let i = 0; i < n; i++) {
    // on the 25-hour day, repeat 01:00 (the repeated hour) so labels are genuinely duplicated
    const hh = di === 0 ? (i <= 1 ? i : i - 1) : i;
    times.push(`${k}T${pad(hh)}:00`);
  }
});
const N = times.length;
const H = {
  time: times,
  temperature_2m: [], apparent_temperature: [], relative_humidity_2m: [], precipitation_probability: [],
  precipitation: [], weather_code: [], cloud_cover: [], visibility: [], wind_speed_10m: [],
  wind_direction_10m: [], wind_gusts_10m: [],
};
for (let i = 0; i < N; i++) {
  const day = Math.floor(i / 24), hr = i % 24;
  const t = 11 + 3 * Math.sin((hr - 9) / 24 * 2 * Math.PI) - day * 0.4;
  H.temperature_2m.push(+t.toFixed(1));
  H.apparent_temperature.push(+(t - 2).toFixed(1));
  H.relative_humidity_2m.push(70 + (i % 10));
  let code = 2, mm = 0, cloud = 40, vis = 24000, pop = 5;
  if (day === 1) { code = 63; mm = 0.4 + (hr % 5) * 0.5; cloud = 95; pop = 80; }   // rainy day
  if (day === 2 && hr < 9) { code = 45; cloud = 100; vis = 600; }                  // morning fog
  if (day === 3) { code = 0; cloud = 0; }                                          // clear
  if (day === 4 && hr > 12) { code = 95; mm = 2.5; cloud = 100; pop = 90; }        // storm
  if (day === 5 && hr > 6 && hr < 18) { code = 73; mm = 0.5; cloud = 90; pop = 70; } // snow
  if (day === 0) { code = hr > 3 ? 3 : 61; cloud = 88; mm = hr <= 3 ? 0.6 : 0; pop = hr <= 3 ? 70 : null; }
  H.weather_code.push(code); H.precipitation.push(mm); H.cloud_cover.push(cloud);
  H.visibility.push(vis); H.precipitation_probability.push(pop);
  H.wind_speed_10m.push(8 + (i % 14)); H.wind_direction_10m.push((i * 17) % 360); H.wind_gusts_10m.push(18 + (i % 14));
}
const daily = {
  time: dayKeys,
  sunrise: dayKeys.map((k, i) => `${k}T0${6 + (i > 0 ? 1 : 0)}:${pad(40 + i)}`),
  sunset: dayKeys.map((k, i) => `${k}T16:${pad(35 - i * 2)}`),
};
const FORECAST = { hourly: H, daily, current: { time: '2026-10-25T21:15', temperature_2m: 10 } };

// ---- environment
const errors = [];
const vc = new VirtualConsole();
vc.on('jsdomError', e => errors.push('jsdomError: ' + e.message));
vc.on('error', e => errors.push('console.error: ' + e));

const noopCtx = new Proxy({}, {
  get: (t, p) => {
    if (p === 'createRadialGradient') return () => ({ addColorStop() {} });
    if (p === 'canvas') return {};
    return () => {};
  },
  set: () => true,
});

const dom = new JSDOM(HTML, {
  runScripts: 'dangerously', pretendToBeVisual: true, url: 'https://weather.test/#greenwich', virtualConsole: vc,
  beforeParse(w) {
    w.HTMLCanvasElement.prototype.getContext = () => noopCtx;
    w.matchMedia = q => ({ matches: reduceMotion && /reduce/.test(q), addEventListener() {}, addListener() {} });
    Object.defineProperty(w.HTMLElement.prototype, 'clientWidth', { get() { return 600; } });
    Object.defineProperty(w.HTMLElement.prototype, 'clientHeight', { get() { return 226; } });
    w.Element.prototype.getBoundingClientRect = function () { return { left: 0, top: 0, width: 600, height: 40, right: 600, bottom: 40 }; };
    w.Element.prototype.scrollIntoView = function () {};
    w.fetch = url => { w.__lastUrl = url; return Promise.resolve({ ok: true, json: () => Promise.resolve(JSON.parse(JSON.stringify(FORECAST))) }); };
  },
});
const w = dom.window, d = w.document;
const sleep = ms => new Promise(r => setTimeout(r, ms));
const txt = id => d.getElementById(id).textContent.trim();
const results = [];
function check(name, ok, extra) { results.push({ name, ok: !!ok, extra }); }
function ptr(node, type, x, pointerType) {
  const e = new w.Event(type, { bubbles: true });
  Object.assign(e, { clientX: x, clientY: 10, button: 0, pointerId: 1, pointerType: pointerType || 'mouse' });
  node.dispatchEvent(e);
}

(async () => {
  await sleep(1800);

  const S = w.__wx.S;
  check('fetch URL has Greenwich lat', /latitude=51\.4826/.test(w.__lastUrl), w.__lastUrl.slice(0, 90));
  check('hours loaded (25+6*24=169)', S.hours.length === 169, S.hours.length);
  check('7 days grouped by date', S.days.length === 7, S.days.map(x => x.n).join(','));
  check('first day has 25 hours', S.days[0].n === 25);
  check('nowIdx points at 21:00', S.hours[S.nowIdx].t.slice(11, 13) === '21' && S.hours[S.nowIdx].day === 0, S.hours[S.nowIdx].t);
  check('hero place name', txt('placeName') === 'Greenwich', txt('placeName'));
  check('hero when label', /^Now · 21:00$/.test(txt('whenLabel')), txt('whenLabel'));
  check('hero condition', txt('cond') === 'Overcast', txt('cond'));
  check('hero number rolled to temp', /^\d+$|^−\d+$/.test(txt('tempNum')), txt('tempNum'));
  check('summary says dry/rain sentence', /Dry|Rain|Snow/.test(txt('summary')), txt('summary'));
  check('stat wind text', /mph/.test(txt('windV')), txt('windV'));
  check('stat daylight text', /\d\d:\d\d.\d\d:\d\d/.test(txt('dayV')), txt('dayV'));
  check('week has 7 rows', d.querySelectorAll('.wrow').length === 7);
  check('today row labelled Today', /^Today/.test(d.querySelector('.wrow .wday').textContent));
  check('ribbon gradients built', [...d.querySelectorAll('.ribbon')].every(r => /linear-gradient/.test(r.getAttribute('style'))));
  check('25 bars on day 0 ribbon, 24 on day 1', d.querySelectorAll('.wrow')[0].querySelectorAll('.rbars i').length === 25 && d.querySelectorAll('.wrow')[1].querySelectorAll('.rbars i').length === 24);
  check('now marker present', !!d.querySelector('.rn'));
  check('Now button hidden at now', d.getElementById('nowBtn').hidden === true);
  check('CSS sky vars set', /rgb\(/.test(d.documentElement.style.getPropertyValue('--sky-top')) && /^\d+,\d+,\d+$/.test(d.documentElement.style.getPropertyValue('--ink')), d.documentElement.style.getPropertyValue('--ink'));
  check('night ink is light (21:00)', +d.documentElement.style.getPropertyValue('--ink').split(',')[0] > 200, d.documentElement.style.getPropertyValue('--ink'));
  const svgAttrs = [...d.querySelectorAll('#curve svg *')].map(n => [...n.attributes].map(a => a.value).join(' ') + n.textContent).join(' ');
  check('curve SVG built, no NaN', d.querySelector('#curve svg .line') && !/NaN|undefined/.test(svgAttrs), (d.querySelector('#curve .line').getAttribute('d') || '').slice(0, 40));
  check('curve cursor label is a temperature', /^−?\d+°$/.test(d.querySelector('#curve .curT').textContent), d.querySelector('#curve .curT').textContent);
  check('hour ticks 00/06/12/18', [...d.querySelectorAll('#curve .ticks text')].map(t => t.textContent).join(',') === '00,06,12,18');
  check('hero marked loaded', !d.getElementById('hero').classList.contains('loading'));
  check('updated footer set', /^Updated \d\d:\d\d$/.test(txt('updated')), txt('updated'));

  // ---- scrub the week ribbon on day 1 (rainy), mouse
  const day1 = d.querySelectorAll('.wrow')[1].querySelector('.ribbon');
  const nightInk = d.documentElement.style.getPropertyValue('--ink');
  ptr(day1, 'pointerdown', 300); // halfway through day 1 => hour 12
  ptr(day1, 'pointerup', 300);
  await sleep(2500);
  check('scrub lands on day 1 12:00', S.hours[S.pos].day === 1 && S.hours[S.pos].t.slice(11, 13) === '12', S.hours[S.pos].t);
  check('when label shows Monday', /^Mon 26 Oct · 12:00$/.test(txt('whenLabel')), txt('whenLabel'));
  check('condition now rain', txt('cond') === 'Rain', txt('cond'));
  check('summary talks about rain now', /^Raining now/.test(txt('summary')), txt('summary'));
  check('Now button appears', d.getElementById('nowBtn').hidden === false);
  check('day 1 row selected', d.querySelectorAll('.wrow')[1].classList.contains('sel') && !d.querySelectorAll('.wrow')[0].classList.contains('sel'));
  check('day title changed', /^Monday 26 October$/.test(txt('dayTitle')), txt('dayTitle'));
  const dayInk = d.documentElement.style.getPropertyValue('--ink');
  check('ink flipped dark for a rainy midday', +dayInk.split(',')[0] < 120, nightInk + ' -> ' + dayInk);
  check('eased rain value reached target', Math.abs(w.__wx.D.rain - w.__wx.T.rain) < 0.01 && w.__wx.D.rain > 0.2, w.__wx.D.rain.toFixed(3));

  // ---- keyboard
  const curve = d.getElementById('curve');
  const before = S.pos;
  curve.dispatchEvent(Object.assign(new w.Event('keydown', { bubbles: true, cancelable: true }), { key: 'ArrowRight' }));
  check('ArrowRight advances one hour', S.pos === before + 1, S.pos);
  curve.dispatchEvent(Object.assign(new w.Event('keydown', { bubbles: true, cancelable: true }), { key: 'Home' }));
  check('Home returns to now', S.pos === S.nowIdx, S.pos);

  // ---- touch: a vertical gesture must not select, a horizontal drag must
  const day3 = d.querySelectorAll('.wrow')[3].querySelector('.ribbon');
  const keep = S.pos;
  ptr(day3, 'pointerdown', 100, 'touch');
  const mv = new w.Event('pointermove', { bubbles: true }); Object.assign(mv, { clientX: 101, clientY: 60, pointerType: 'touch', pointerId: 1 });
  day3.dispatchEvent(mv);
  day3.dispatchEvent(new w.Event('pointercancel', { bubbles: true }));
  check('vertical touch scroll leaves selection alone', S.pos === keep);
  ptr(day3, 'pointerdown', 100, 'touch');
  const mv2 = new w.Event('pointermove', { bubbles: true }); Object.assign(mv2, { clientX: 400, clientY: 12, pointerType: 'touch', pointerId: 1 });
  day3.dispatchEvent(mv2);
  ptr(day3, 'pointerup', 400, 'touch');
  check('horizontal touch drag scrubs', S.hours[S.pos].day === 3, S.hours[S.pos].t);

  // ---- every day: pick every hour, make sure nothing throws or goes NaN
  let bad = null;
  for (let i = 0; i < S.hours.length && !bad; i++) {
    try { w.__wx.S.pos = i; /* via public path */ const r = d.querySelectorAll('.wrow')[S.hours[i].day].querySelector('.ribbon'); const dd = S.days[S.hours[i].day]; ptr(r, 'pointerdown', ((S.hours[i].k + 0.5) / dd.n) * 600); ptr(r, 'pointerup', 0); }
    catch (e) { bad = 'threw at ' + i + ': ' + e.message; }
    const h = S.hours[S.pos];
    if (!bad && (h.top.some(Number.isNaN) || h.bot.some(Number.isNaN) || Number.isNaN(h.sunX) || Number.isNaN(h.moonY))) bad = 'NaN at ' + h.t;
    if (!bad && S.pos !== i) bad = 'scrub mismatch at ' + i + ' got ' + S.pos;
  }
  check('scrubbing all 169 hours is clean', !bad, bad);
  await sleep(600);
  const svgAttrs2 = [...d.querySelectorAll('#curve svg *')].map(n => [...n.attributes].map(a => a.value).join(' ') + n.textContent).join(' ');
  check('no NaN in curve after scrub sweep', !/NaN|undefined/.test(svgAttrs2));

  // ---- palette sanity across the day, clear weather
  const bp = w.__wx.basePalette, sky = w.__wx.skyFor;
  const none = { cloud: 0, rain: 0, fog: 0, snow: 0, storm: 0 };
  const lum = c => (0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]) / 255;
  const noon = sky(...bp(1, true), none, 1), night = sky(...bp(-1, true), none, -1);
  check('noon sky much brighter than night', lum(noon[0]) > lum(night[0]) + 0.3, lum(noon[0]).toFixed(2) + ' vs ' + lum(night[0]).toFixed(2));
  const clear = sky(...bp(0.8, true), none, 0.8), over = sky(...bp(0.8, true), { cloud: 1, rain: 0.7, fog: 0, snow: 0, storm: 0 }, 0.8);
  const sat = c => Math.max(...c) - Math.min(...c);
  check('rain + cloud desaturates the sky', sat(over[0]) < sat(clear[0]) * 0.7, sat(clear[0]).toFixed(0) + ' -> ' + sat(over[0]).toFixed(0));
  check('palette is continuous across sunrise', (() => { let worst = 0, prev = null; for (let a = -1; a <= 1; a += 0.01) { const c = bp(a, true)[1]; if (prev) worst = Math.max(worst, Math.hypot(c[0] - prev[0], c[1] - prev[1], c[2] - prev[2])); prev = c; } return worst < 14; })());

  // ---- place switch keeps the selected time
  d.querySelectorAll('.chip')[0].dispatchEvent(new w.Event('click', { bubbles: true }));
  await sleep(1500);
  check('place switch updates label and URL', txt('placeName') === 'Central London' && /latitude=51\.5074/.test(w.__lastUrl), txt('placeName'));
  check('hash updated', w.location.hash === '#london', w.location.hash);
  check('selected time kept across place switch', S.hours[S.pos].t === S.selTime);

  console.log(`\n=== smoke test (${reduceMotion ? 'reduced motion' : 'full motion'}) ===`);
  let fail = 0;
  results.forEach(r => { if (!r.ok) fail++; console.log((r.ok ? 'PASS ' : 'FAIL ') + r.name + (r.ok && !r.extra ? '' : r.extra !== undefined ? '   [' + r.extra + ']' : '')); });
  console.log(`\n${results.length - fail}/${results.length} passed; ${errors.length} runtime errors`);
  errors.forEach(e => console.log('  ' + e));
  process.exit(fail || errors.length ? 1 : 0);
})();
