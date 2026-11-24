// Service worker of the BACK OFFICE (scope /admin/). The front office (/) has its own service worker.
// Increase CACHE_VERSION whenever the shell changes, to renew the cache.
const CACHE_PREFIX = 'sp-back-';
const CACHE_VERSION = 'v1';
const CACHE = `${CACHE_PREFIX}${CACHE_VERSION}`;

const SHELL = [
  '/admin/',
  '/admin/index.html',
  '/admin/dashboard.html',
  '/admin/live-map.html',
  '/admin/calibration.html',
  '/admin/offline.html',
  '/admin/manifest.json',
  '/admin/js/login.js',
  '/admin/js/calibration.js',
  '/admin/js/spot-validation.js',
  '/admin/css/calibration.css',
  '/shared/css/base.css',
  '/shared/css/parking-map.css',
  '/shared/js/pwa.js',
  '/shared/js/session.js',
  '/shared/js/protected-page.js',
  '/shared/js/realtime.js',
  '/shared/js/live-map.js',
  '/shared/icons/icon-192.png',
  '/shared/icons/icon-512.png',
  '/shared/icons/icon-maskable-512.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE).then((cache) => cache.addAll(SHELL)).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  // Only the old caches of THIS application are removed (the front office has its own).
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys
        .filter((k) => k !== CACHE && k.startsWith(CACHE_PREFIX))
        .map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  const url = new URL(request.url);

  // Only same-site GET requests go through the cache; the API and the WebSocket never do.
  if (request.method !== 'GET' || url.origin !== self.location.origin || url.pathname.startsWith('/api')) {
    return;
  }

  // Pages: network first (operator data must be fresh), then the cache, then the offline page.
  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request)
        .then((response) => keep(request, response))
        .catch(() => caches.match(request).then((r) => r || caches.match('/admin/offline.html')))
    );
    return;
  }

  // Static resources: cache first.
  event.respondWith(
    caches.match(request).then((cached) => cached || fetch(request).then((response) => keep(request, response)))
  );
});

// Only successful responses are cached (never a 404 or a server error).
function keep(request, response) {
  if (response.ok) {
    const copy = response.clone();
    caches.open(CACHE).then((cache) => cache.put(request, copy));
  }
  return response;
}
