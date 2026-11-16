// Pages réservées (conducteur.html, exploitant.html) : contrôle du rôle, nom affiché, déconnexion.
// Le rôle attendu est dans <body data-role="...">. La vraie protection reste le serveur (@RolesAllowed).
import { appelerApi, deconnexion, exigerRole } from './auth.js';

const utilisateur = exigerRole(document.body.dataset.role);

if (utilisateur) {
  const nom = document.getElementById('nom-utilisateur');
  if (nom) nom.textContent = utilisateur.nom || utilisateur.email || '';
  document.getElementById('btn-deconnexion')?.addEventListener('click', deconnexion);
  document.body.hidden = false;

  // Le serveur confirme que le jeton est toujours valable (un 401 renvoie vers la connexion).
  appelerApi('/api/auth/moi').then((r) => {
    if (r.statut === 200 && r.corps && nom) nom.textContent = r.corps.nom || r.corps.email;
  });
}
