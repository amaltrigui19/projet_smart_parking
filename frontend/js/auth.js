// Session de l'utilisateur : jeton JWT, rôle, appels à l'API (contrat docs/contrats.md §1.2 et §2.5).
// Module partagé par toutes les pages de la PWA. Aucune sécurité ici : le serveur vérifie le rôle à chaque appel.

// Contrat §1.2 : le jeton est gardé dans sessionStorage, clé « jwt » (calibration.js le lit au même endroit).
export const CLE_JWT = 'jwt';
const CLE_UTILISATEUR = 'utilisateur';
const CLE_EXPIRATION = 'jwt_expire_le'; // en millisecondes depuis 1970

export const PAGES = {
  connexion: 'connexion.html',
  CONDUCTEUR: 'conducteur.html', // front-office
  EXPLOITANT: 'exploitant.html', // back-office
};

const stockage = () => window.sessionStorage;

function lire(cle) {
  try { return stockage().getItem(cle); } catch { return null; }
}

// Charge utile du JWT (sans vérifier la signature : seul le serveur le fait). null si illisible.
function chargeUtile(jeton) {
  const morceaux = String(jeton).split('.');
  if (morceaux.length !== 3) return null;
  try {
    const b64 = morceaux[1].replace(/-/g, '+').replace(/_/g, '/');
    const octets = Uint8Array.from(atob(b64 + '='.repeat((4 - (b64.length % 4)) % 4)), (c) => c.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(octets)); // UTF-8 : noms avec accents
  } catch {
    return null;
  }
}

// Le jeton s'il existe et n'est pas expiré, sinon null (et la session est effacée).
export function lireJeton() {
  const jeton = lire(CLE_JWT);
  if (!jeton) return null;
  const expire = Number(lire(CLE_EXPIRATION)) || (chargeUtile(jeton)?.exp ?? 0) * 1000;
  if (expire && Date.now() >= expire) {
    effacerSession();
    return null;
  }
  return jeton;
}

// L'utilisateur connecté ({ id, nom, email, role }), ou null.
export function utilisateurConnecte() {
  const jeton = lireJeton();
  if (!jeton) return null;
  let utilisateur = null;
  try { utilisateur = JSON.parse(lire(CLE_UTILISATEUR)); } catch { /* illisible */ }
  // Rôle renvoyé par la connexion (utilisateur.role, demandé par le serveur) ; à défaut, celui du jeton.
  // Le serveur met le même rôle dans la revendication « role » du JWT.
  const role = utilisateur?.role ?? chargeUtile(jeton)?.role;
  if (role !== 'CONDUCTEUR' && role !== 'EXPLOITANT') return null;
  return { ...(utilisateur || {}), role };
}

export function effacerSession() {
  try {
    stockage().removeItem(CLE_JWT);
    stockage().removeItem(CLE_UTILISATEUR);
    stockage().removeItem(CLE_EXPIRATION);
  } catch { /* stockage indisponible : rien à effacer */ }
}

function ouvrirSession({ jeton, expire_dans, utilisateur }) {
  stockage().setItem(CLE_JWT, jeton);
  stockage().setItem(CLE_UTILISATEUR, JSON.stringify(utilisateur));
  if (expire_dans) stockage().setItem(CLE_EXPIRATION, String(Date.now() + expire_dans * 1000));
}

export const pageDuRole = (role) => PAGES[role] || PAGES.connexion;

// Appel à l'API. Renvoie { statut, corps } ; statut 0 = serveur injoignable.
// avecJeton : false pour l'inscription et la connexion. Le serveur refuse en 401 tout jeton invalide,
// même sur une route publique : on n'envoie donc jamais d'en-tête Authorization sur ces deux routes.
export async function appelerApi(chemin, { methode = 'GET', corps, avecJeton = true } = {}) {
  const entetes = { Accept: 'application/json' };
  if (corps !== undefined) entetes['Content-Type'] = 'application/json';
  if (avecJeton) {
    const jeton = lireJeton();
    if (jeton) entetes.Authorization = `Bearer ${jeton}`;
  }
  let reponse;
  try {
    reponse = await fetch(chemin, {
      method: methode,
      headers: entetes,
      body: corps === undefined ? undefined : JSON.stringify(corps),
      cache: 'no-store',
    });
  } catch {
    return { statut: 0, corps: null };
  }
  let json = null;
  try { json = await reponse.json(); } catch { /* réponse sans JSON */ }
  // Contrat §1.2 : sur 401 d'une route protégée, on efface le jeton et on revient à la connexion.
  if (reponse.status === 401 && avecJeton) {
    effacerSession();
    allerALaConnexion(true);
  }
  return { statut: reponse.status, corps: json };
}

// Message à afficher pour une erreur : le champ « message » du serveur (contrat §1.1) si possible.
export function messageErreur({ statut, corps }, parDefaut = {}) {
  if (statut === 0) return 'Serveur injoignable : vérifiez votre connexion, puis réessayez.';
  if (parDefaut[statut]) return parDefaut[statut];
  if (corps && corps.message) return corps.message;
  return `Erreur inattendue du serveur (${statut}).`;
}

export async function connexion(email, motDePasse) {
  const r = await appelerApi('/api/auth/connexion', {
    methode: 'POST', corps: { email, mot_de_passe: motDePasse }, avecJeton: false,
  });
  if (r.statut === 200 && r.corps && r.corps.jeton && r.corps.utilisateur) ouvrirSession(r.corps);
  return r;
}

export function inscription(nom, email, motDePasse) {
  return appelerApi('/api/auth/inscription', {
    methode: 'POST', corps: { nom, email, mot_de_passe: motDePasse }, avecJeton: false,
  });
}

// sessionExpiree : la page de connexion affiche « votre session a expiré » (aucune donnée dans l'URL).
export function allerALaConnexion(sessionExpiree = false) {
  location.replace(PAGES.connexion + (sessionExpiree ? '?expire' : ''));
}

export function deconnexion() {
  effacerSession();
  allerALaConnexion();
}

// À appeler en haut de chaque page réservée : renvoie l'utilisateur, ou redirige.
// Pas connecté → connexion ; mauvais rôle → sa propre interface.
export function exigerRole(roleAttendu) {
  const utilisateur = utilisateurConnecte();
  if (!utilisateur) {
    effacerSession();
    allerALaConnexion();
    return null;
  }
  if (roleAttendu && utilisateur.role !== roleAttendu) {
    location.replace(pageDuRole(utilisateur.role));
    return null;
  }
  return utilisateur;
}
