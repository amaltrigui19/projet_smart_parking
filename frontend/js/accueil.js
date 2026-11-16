// Page d'accueil : si l'utilisateur est déjà connecté, le bouton mène à son interface.
import { pageDuRole, utilisateurConnecte } from './auth.js';

const lien = document.getElementById('lien-espace');
const utilisateur = utilisateurConnecte();
if (lien && utilisateur) {
  lien.href = pageDuRole(utilisateur.role);
  lien.textContent = utilisateur.role === 'EXPLOITANT' ? 'Ouvrir l\'espace exploitant' : 'Ouvrir l\'espace conducteur';
}
