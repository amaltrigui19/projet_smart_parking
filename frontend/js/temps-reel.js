// État des places en temps réel (contrat docs/contrats.md §1.4 et §2.6), indépendant du dessin du plan.
//
//   1. ouvre le WebSocket /api/ws/places?token=<jwt> ;
//   2. dès qu'il est ouvert, charge l'état complet (GET /api/places), puis applique les messages
//      { place, etat, depuis } (ceux reçus pendant le chargement sont gardés, puis appliqués) ;
//   3. si le WebSocket se coupe : reconnexion automatique (1 s, 2 s, 5 s, puis 10 s au maximum)
//      et nouveau chargement de l'état complet à chaque reconnexion ;
//   4. jeton absent, expiré ou refusé (401) : session effacée et retour à la connexion (§1.2).
//
// Utilisation par le plan SVG (convention : chaque place porte data-place="A3") :
//   import { brancherPlan, afficherStatut } from './temps-reel.js';
//   brancherPlan(document.querySelector('svg'), { surStatut: afficherStatut(pastille) });
// ou, sans toucher au DOM :
//   demarrerTempsReel({ surChangement(place, etat, info) { ... } });
import { allerALaConnexion, appelerApi, effacerSession, lireJeton } from './auth.js';

export const ETATS = ['libre', 'occupee', 'reservee']; // = les classes CSS des places
export const DELAIS_RECONNEXION_MS = [1000, 2000, 5000, 10000];
// Fermeture du WebSocket par le serveur pour un jeton refusé (proposition au serveur : 4401 ; 1008 = « policy violation »).
export const CODES_JETON_REFUSE = [4401, 1008];
export const STATUTS = {
  CONNEXION: 'connexion',
  CONNECTE: 'connecte',
  RECONNEXION: 'reconnexion',
  HORS_LIGNE: 'hors-ligne',
};

const ID_PLACE = /^[A-Za-z0-9_-]+$/;

// wss:// en HTTPS (VM, serveur local), ws:// sur http://localhost (fausse API).
export function urlWebSocket(jeton, adresse = globalThis.location) {
  const protocole = adresse.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocole}//${adresse.host}/api/ws/places?token=${encodeURIComponent(jeton)}`;
}

const instant = (iso) => {
  const t = Date.parse(iso);
  return Number.isNaN(t) ? null : t;
};

export function demarrerTempsReel(options = {}) {
  const {
    surChangement = () => {}, // (place, etat, info) : pour chaque place, à l'état complet puis à chaque message
    surEtatComplet = () => {}, // (places : Map) après chaque chargement de l'état complet
    surStatut = () => {}, // (statut, detail) : voir STATUTS
    surAutreMessage = () => {}, // messages avec un champ « type » (événements, statut du Pi : proposition §2.6)
    // Dépendances remplaçables (tests automatiques)
    WebSocket: ClasseWebSocket = globalThis.WebSocket,
    chargerPlaces = () => appelerApi('/api/places'),
    lire = lireJeton,
    url = (jeton) => urlWebSocket(jeton),
    sessionExpiree = () => { effacerSession(); allerALaConnexion(true); },
    enLigne = () => (typeof navigator === 'undefined' ? true : navigator.onLine),
    minuterie = { poser: (f, ms) => setTimeout(f, ms), annuler: (id) => clearTimeout(id) },
    delais = DELAIS_RECONNEXION_MS,
  } = options;

  const places = new Map(); // id -> { id, etat, depuis, type, duree_max_min }
  let socket = null; // WebSocket en cours (les événements des anciens sont ignorés)
  let enAttente = null; // messages reçus avant la fin du chargement de l'état complet
  let echecs = 0; // tentatives ratées depuis la dernière connexion réussie
  let minuteur = null;
  let arrete = false;
  let statut = null;

  function changerStatut(nouveau, detail = {}) {
    statut = nouveau;
    surStatut(nouveau, detail);
  }

  // Un message { place, etat, depuis }. Ignoré s'il est mal formé ou plus ancien que l'état connu.
  function appliquer(message) {
    if (!message || typeof message.place !== 'string' || !ID_PLACE.test(message.place)) return false;
    if (!ETATS.includes(message.etat)) return false;
    const actuelle = places.get(message.place);
    const t = instant(message.depuis);
    const tActuel = actuelle ? instant(actuelle.depuis) : null;
    if (t !== null && tActuel !== null && t < tActuel) return false;
    const info = { ...actuelle, id: message.place, etat: message.etat, depuis: message.depuis ?? actuelle?.depuis };
    places.set(message.place, info);
    surChangement(message.place, message.etat, info);
    return true;
  }

  function traiter(message) {
    if (message && typeof message === 'object' && 'type' in message) surAutreMessage(message);
    else appliquer(message);
  }

  // Réponse de GET /api/places : elle fait foi, elle remplace l'état connu.
  function appliquerEtatComplet(corps) {
    for (const p of corps.places) {
      if (!p || typeof p.id !== 'string' || !ID_PLACE.test(p.id) || !ETATS.includes(p.etat)) continue;
      const info = { ...p };
      places.set(p.id, info);
      surChangement(p.id, p.etat, info);
    }
    surEtatComplet(places);
  }

  const reponseValide = (r) => r && r.statut === 200 && r.corps && Array.isArray(r.corps.places);

  function finirSession() {
    arreter();
    changerStatut(STATUTS.HORS_LIGNE, { raison: 'session' });
    sessionExpiree();
  }

  function ouvrir() {
    minuteur = null;
    if (arrete || socket) return;
    const jeton = lire();
    if (!jeton) return finirSession();
    if (statut === null) changerStatut(STATUTS.CONNEXION);

    let ws;
    try {
      ws = new ClasseWebSocket(url(jeton));
    } catch {
      return apresFermeture(1006, false);
    }
    socket = ws;
    enAttente = [];
    let ouvert = false;

    ws.onopen = async () => {
      if (ws !== socket) return;
      ouvert = true;
      const r = await chargerPlaces();
      if (ws !== socket || arrete) return;
      if (reponseValide(r)) {
        appliquerEtatComplet(r.corps);
        const messages = enAttente;
        enAttente = null;
        messages.forEach(traiter);
        echecs = 0;
        changerStatut(STATUTS.CONNECTE);
      } else if (r && r.statut === 401) {
        finirSession();
      } else {
        ws.close(); // état complet indisponible : on recommence plus tard
      }
    };
    ws.onmessage = (evenement) => {
      if (ws !== socket) return;
      let message;
      try { message = JSON.parse(evenement.data); } catch { return; }
      if (enAttente) enAttente.push(message);
      else traiter(message);
    };
    ws.onerror = () => {}; // toujours suivi de onclose
    ws.onclose = (evenement) => {
      if (ws !== socket) return;
      socket = null;
      enAttente = null;
      apresFermeture(evenement?.code, ouvert);
    };
  }

  async function apresFermeture(code, avaitEteOuvert) {
    if (arrete) return;
    if (CODES_JETON_REFUSE.includes(code)) return finirSession();
    let injoignable = !enLigne();
    // Connexion refusée avant l'ouverture : un navigateur ne donne pas le code HTTP du refus.
    // L'API REST le donne : 401 = jeton refusé, 0 = serveur injoignable. Si elle répond, on garde le plan à jour.
    if (!avaitEteOuvert && !injoignable) {
      const r = await chargerPlaces();
      if (arrete || socket) return; // arrêté, ou déjà reconnecté entre-temps (retour du réseau)
      if (r && r.statut === 401) return finirSession();
      if (reponseValide(r)) appliquerEtatComplet(r.corps);
      injoignable = !r || r.statut === 0;
    }
    const delai = delais[Math.min(echecs, delais.length - 1)];
    echecs += 1;
    changerStatut(injoignable ? STATUTS.HORS_LIGNE : STATUTS.RECONNEXION, { delai, tentative: echecs });
    minuteur = minuterie.poser(ouvrir, delai);
  }

  // Nouvelle tentative tout de suite (par exemple au retour du réseau).
  function reconnecterMaintenant() {
    if (arrete || socket) return;
    if (minuteur !== null) minuterie.annuler(minuteur);
    ouvrir();
  }

  function arreter() {
    arrete = true;
    if (minuteur !== null) minuterie.annuler(minuteur);
    minuteur = null;
    const ws = socket;
    socket = null;
    if (ws) ws.close(1000);
    if (typeof window !== 'undefined') window.removeEventListener('online', reconnecterMaintenant);
  }

  if (typeof window !== 'undefined') window.addEventListener('online', reconnecterMaintenant);
  ouvrir();

  return {
    places,
    arreter,
    reconnecterMaintenant,
    get statut() { return statut; },
  };
}

// ---------- Aide pour le plan SVG ----------

// Met la classe libre / occupee / reservee sur l'élément [data-place="<place>"] du plan.
export function colorerPlace(racine, place, etat) {
  if (!ID_PLACE.test(place) || !ETATS.includes(etat)) return false;
  const element = racine.querySelector(`[data-place="${place}"]`);
  if (!element) return false;
  element.classList.remove(...ETATS);
  element.classList.add(etat);
  element.setAttribute('data-etat', etat);
  return true;
}

// Branche un plan SVG (ou tout élément contenant des [data-place]) sur le temps réel.
export function brancherPlan(racine, options = {}) {
  return demarrerTempsReel({
    ...options,
    surChangement(place, etat, info) {
      colorerPlace(racine, place, etat);
      options.surChangement?.(place, etat, info);
    },
  });
}

// Renvoie une fonction surStatut qui affiche l'état de la connexion dans un élément (classe statut-<statut>).
export function afficherStatut(element) {
  return (statut, detail = {}) => {
    const textes = {
      [STATUTS.CONNEXION]: 'Connexion…',
      [STATUTS.CONNECTE]: 'Temps réel : connecté',
      [STATUTS.RECONNEXION]: `Reconnexion dans ${Math.round((detail.delai || 0) / 1000)} s…`,
      [STATUTS.HORS_LIGNE]: detail.raison === 'session'
        ? 'Session expirée'
        : `Hors ligne · nouvel essai dans ${Math.round((detail.delai || 0) / 1000)} s`,
    };
    element.textContent = textes[statut] || statut;
    for (const s of Object.values(STATUTS)) element.classList.remove(`statut-${s}`);
    element.classList.add(`statut-${statut}`);
  };
}
