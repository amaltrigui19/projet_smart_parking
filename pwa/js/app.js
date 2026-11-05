// Point d'entrée de la PWA : service worker, installation, état du réseau.

const pastille = document.getElementById('statut-reseau');
const boutonInstaller = document.getElementById('btn-installer');
let invitationInstallation = null;

function majReseau() {
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
