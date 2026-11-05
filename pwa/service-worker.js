// Incrémenter CACHE_VERSION à chaque changement de la coquille pour renouveler le cache.
const CACHE_VERSION = 'v1';
const CACHE = `smart-parking-${CACHE_VERSION}`;

const COQUILLE = [
  './',
  'index.html',
  'offline.html',
  'manifest.json',
  'css/style.css',
  'js/app.js',
  'icons/icon-192.png',
  'icons/icon-512.png',
  'icons/icon-maskable-512.png',
];

self.addEventListener('install', (evenement) => {
  evenement.waitUntil(
    caches.open(CACHE).then((cache) => cache.addAll(COQUILLE)).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (evenement) => {
  evenement.waitUntil(
    caches.keys()
      .then((cles) => Promise.all(cles.filter((c) => c !== CACHE).map((c) => caches.delete(c))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (evenement) => {
  const requete = evenement.request;
  const url = new URL(requete.url);

  // Seules les requêtes GET du même site passent par le cache ; l'API et le WebSocket ne sont jamais mis en cache.
  if (requete.method !== 'GET' || url.origin !== self.location.origin || url.pathname.startsWith('/api')) {
    return;
  }

  // Pages : réseau d'abord, repli sur le cache puis sur la page hors ligne.
  if (requete.mode === 'navigate') {
    evenement.respondWith(
      fetch(requete)
        .then((reponse) => garder(requete, reponse))
        .catch(() => caches.match(requete).then((r) => r || caches.match('offline.html')))
    );
    return;
  }

  // Ressources statiques : cache d'abord.
  evenement.respondWith(
    caches.match(requete).then((enCache) => enCache || fetch(requete).then((reponse) => garder(requete, reponse)))
  );
});

// Ne met en cache que les réponses réussies (jamais un 404 ou une erreur serveur).
function garder(requete, reponse) {
  if (reponse.ok) {
    const copie = reponse.clone();
    caches.open(CACHE).then((cache) => cache.put(requete, copie));
  }
  return reponse;
}
