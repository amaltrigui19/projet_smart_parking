// Page de connexion et d'inscription (contrat §2.5). Après la connexion : redirection selon le rôle.
import { connexion, inscription, messageErreur, pageDuRole, utilisateurConnecte } from './auth.js';

// Déjà connecté : on va directement dans son interface.
const dejaConnecte = utilisateurConnecte();
if (dejaConnecte) location.replace(pageDuRole(dejaConnecte.role));

const el = (id) => document.getElementById(id);
const formConnexion = el('form-connexion');
const formInscription = el('form-inscription');
const ongletConnexion = el('onglet-connexion');
const ongletInscription = el('onglet-inscription');
const message = el('message-auth');

const EMAIL_VALIDE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function afficher(genre, texte) {
  message.hidden = !texte;
  message.className = `message ${genre}`;
  message.setAttribute('role', genre === 'erreur' ? 'alert' : 'status');
  message.textContent = texte;
}

function choisirOnglet(inscrire) {
  ongletConnexion.setAttribute('aria-selected', String(!inscrire));
  ongletInscription.setAttribute('aria-selected', String(inscrire));
  formConnexion.hidden = inscrire;
  formInscription.hidden = !inscrire;
  afficher('info', '');
  (inscrire ? formInscription : formConnexion).elements[0].focus();
}
ongletConnexion.addEventListener('click', () => choisirOnglet(false));
ongletInscription.addEventListener('click', () => choisirOnglet(true));

function occuper(form, occupe) {
  for (const champ of form.elements) champ.disabled = occupe;
}

// Connexion réussie : redirection selon utilisateur.role (conducteur → front-office, exploitant → back-office).
async function seConnecter(email, motDePasse) {
  const r = await connexion(email, motDePasse);
  if (r.statut === 200) {
    const utilisateur = utilisateurConnecte();
    if (utilisateur) {
      location.replace(pageDuRole(utilisateur.role));
      return true;
    }
    afficher('erreur', 'Réponse du serveur illisible : rôle inconnu.');
    return false;
  }
  afficher('erreur', messageErreur(r, { 401: 'Email ou mot de passe incorrect.' }));
  return false;
}

formConnexion.addEventListener('submit', async (evt) => {
  evt.preventDefault();
  const email = formConnexion.elements.email.value.trim().toLowerCase();
  const motDePasse = formConnexion.elements.mot_de_passe.value;
  if (!EMAIL_VALIDE.test(email) || !motDePasse) {
    afficher('erreur', 'Saisissez votre email et votre mot de passe.');
    return;
  }
  occuper(formConnexion, true);
  afficher('info', 'Connexion…');
  const ok = await seConnecter(email, motDePasse);
  occuper(formConnexion, false);
  if (!ok) formConnexion.elements.mot_de_passe.select();
});

formInscription.addEventListener('submit', async (evt) => {
  evt.preventDefault();
  const f = formInscription.elements;
  const nom = f.nom.value.trim();
  const email = f.email.value.trim().toLowerCase();
  const motDePasse = f.mot_de_passe.value;
  // Mêmes règles que le serveur (ValidationInscription) ; le serveur reste celui qui décide.
  let erreur = '';
  if (!nom) erreur = 'Le nom est obligatoire.';
  else if (!EMAIL_VALIDE.test(email)) erreur = "L'adresse email n'est pas valide.";
  else if (motDePasse.length < 8 || motDePasse.length > 128) erreur = 'Le mot de passe doit faire entre 8 et 128 caractères.';
  else if (motDePasse !== f.confirmation.value) erreur = 'Les deux mots de passe ne sont pas identiques.';
  if (erreur) { afficher('erreur', erreur); return; }

  occuper(formInscription, true);
  afficher('info', 'Création du compte…');
  const r = await inscription(nom, email, motDePasse);
  if (r.statut === 201) {
    // L'inscription ne renvoie pas de jeton : on enchaîne avec la connexion.
    afficher('succes', 'Compte créé. Connexion…');
    const ok = await seConnecter(email, motDePasse);
    if (!ok) {
      occuper(formInscription, false);
      choisirOnglet(false);
      formConnexion.elements.email.value = email;
      afficher('succes', 'Compte créé. Connectez-vous avec votre email et votre mot de passe.');
    }
    return;
  }
  occuper(formInscription, false);
  afficher('erreur', messageErreur(r, { 409: 'Un compte existe déjà avec cet email. Connectez-vous.' }));
});

// Si on arrive ici après un 401 sur une autre page : un mot d'explication.
if (new URLSearchParams(location.search).has('expire')) {
  afficher('info', 'Votre session a expiré : reconnectez-vous.');
}
