// Point d'entrée commun aux pages de la PWA : service worker, installation, état du réseau.
// Chaque élément est facultatif : une page sans bouton « Installer » ou sans pastille fonctionne aussi.

const pastille = document.getElementById('statut-reseau');
const boutonInstaller = document.getElementById('btn-installer');
let invitationInstallation = null;

function majReseau() {
  if (!pastille) return;
  const enLigne = navigator.onLine;
  pastille.textContent = enLigne ? 'En ligne' : 'Hors ligne';
  pastille.classList.toggle('hors-ligne', !enLigne);
}
window.addEventListener('online', majReseau);
window.addEventListener('offline', majReseau);
majReseau();

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('service-worker.js').catch((erreur) => {
      console.error('Service worker non enregistré :', erreur);
    });
  });
}

if (boutonInstaller) {
  // Le navigateur propose l'installation : on garde l'événement pour l'afficher sur notre bouton.
  window.addEventListener('beforeinstallprompt', (evenement) => {
    evenement.preventDefault();
    invitationInstallation = evenement;
    boutonInstaller.hidden = false;
  });

  boutonInstaller.addEventListener('click', async () => {
    if (!invitationInstallation) return;
    invitationInstallation.prompt();
    await invitationInstallation.userChoice;
    invitationInstallation = null;
    boutonInstaller.hidden = true;
  });

  window.addEventListener('appinstalled', () => {
    boutonInstaller.hidden = true;
  });
}
