// Common entry point of the pages: service worker, install button, network status.
// Each element is optional: a page without an "Install" button or a status badge works too.
// The service worker is the one of the current application: /service-worker.js (front office)
// or /admin/service-worker.js (back office), given by <html data-service-worker="...">.

const networkBadge = document.getElementById('network-status');
const installButton = document.getElementById('install-button');
let installPrompt = null;

function updateNetwork() {
  if (!networkBadge) return;
  const online = navigator.onLine;
  networkBadge.textContent = online ? 'En ligne' : 'Hors ligne';
  networkBadge.classList.toggle('offline', !online);
}
window.addEventListener('online', updateNetwork);
window.addEventListener('offline', updateNetwork);
updateNetwork();

const serviceWorkerUrl = document.documentElement.dataset.serviceWorker;

if ('serviceWorker' in navigator && serviceWorkerUrl) {
  // An update activates a new service worker (skipWaiting + clients.claim in the service worker), but the
  // page already open keeps the old resources until it is reloaded: we reload it once here.
  let reloading = false;
  navigator.serviceWorker.addEventListener('controllerchange', () => {
    if (reloading) return;
    reloading = true;
    window.location.reload();
  });

  window.addEventListener('load', () => {
    navigator.serviceWorker.register(serviceWorkerUrl).catch((error) => {
      console.error('Service worker non enregistré :', error);
    });
  });
}

if (installButton) {
  // The browser offers installation: keep the event to show it on our own button.
  window.addEventListener('beforeinstallprompt', (event) => {
    event.preventDefault();
    installPrompt = event;
    installButton.hidden = false;
  });

  installButton.addEventListener('click', async () => {
    if (!installPrompt) return;
    installPrompt.prompt();
    await installPrompt.userChoice;
    installPrompt = null;
    installButton.hidden = true;
  });

  window.addEventListener('appinstalled', () => {
    installButton.hidden = true;
  });
}
