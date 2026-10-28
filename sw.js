/* Service worker: lets the site install and open offline.
   - Shell (same-origin files): network first, so a deploy shows up straight
     away; falls back to the cached copy when offline.
   - Forecast: network first, falls back to the last forecast fetched for
     that place, so offline you see the most recent one rather than an error.
   - Fraunces font files: cache first (they never change).
   Place search (geocoding) is never cached. Bump VERSION to drop old caches. */
var VERSION = 'v1';
var SHELL = 'wx-shell-' + VERSION, DATA = 'wx-data-' + VERSION, FONTS = 'wx-fonts-' + VERSION;
var SHELL_FILES = ['./', 'manifest.webmanifest', 'icons/icon-192.png', 'icons/icon-512.png', 'icons/apple-touch-icon.png'];

self.addEventListener('install', function (e) {
  e.waitUntil(caches.open(SHELL).then(function (c) { return c.addAll(SHELL_FILES); }).then(function () { return self.skipWaiting(); }));
});

self.addEventListener('activate', function (e) {
  var keep = [SHELL, DATA, FONTS];
  e.waitUntil(caches.keys().then(function (keys) {
    return Promise.all(keys.filter(function (k) { return keep.indexOf(k) < 0; }).map(function (k) { return caches.delete(k); }));
  }).then(function () { return self.clients.claim(); }));
});

function networkFirst(req, name) {
  return fetch(req).then(function (res) {
    if (res && res.ok) { var copy = res.clone(); caches.open(name).then(function (c) { c.put(req, copy); }); }
    return res;
  }).catch(function () {
    return caches.match(req, { ignoreSearch: name === SHELL }).then(function (hit) { return hit || Promise.reject(new Error('offline')); });
  });
}

function cacheFirst(req, name) {
  return caches.match(req).then(function (hit) {
    return hit || fetch(req).then(function (res) {
      if (res && (res.ok || res.type === 'opaque')) { var copy = res.clone(); caches.open(name).then(function (c) { c.put(req, copy); }); }
      return res;
    });
  });
}

self.addEventListener('fetch', function (e) {
  var req = e.request;
  if (req.method !== 'GET') return;
  var url = new URL(req.url);
  if (url.origin === self.location.origin) {
    e.respondWith(networkFirst(req, SHELL));
  } else if (url.hostname === 'api.open-meteo.com') {
    e.respondWith(networkFirst(req, DATA));
  } else if (url.hostname === 'fonts.gstatic.com' || url.hostname === 'fonts.googleapis.com') {
    e.respondWith(cacheFirst(req, FONTS));
  }
});
