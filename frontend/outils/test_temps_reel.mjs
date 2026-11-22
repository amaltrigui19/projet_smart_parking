// Tests de frontend/js/temps-reel.js (contrat docs/contrats.md §1.4), avec un faux WebSocket et une fausse minuterie.
// Lancer depuis la racine du dépôt : node frontend/outils/test_temps_reel.mjs
import assert from 'node:assert/strict';
import { demarrerTempsReel, urlWebSocket, colorerPlace, DELAIS_RECONNEXION_MS, STATUTS } from '../js/temps-reel.js';

// ---------- Doublures ----------
class FauxWebSocket {
  static instances = [];
  constructor(url) { this.url = url; this.ferme = false; FauxWebSocket.instances.push(this); }
  ouvrir() { this.onopen?.(); }
  recevoir(objet) { this.onmessage?.({ data: typeof objet === 'string' ? objet : JSON.stringify(objet) }); }
  couper(code = 1006) { this.ferme = true; this.onclose?.({ code }); }
  close(code = 1000) { if (!this.ferme) this.couper(code); }
}
const dernier = () => FauxWebSocket.instances.at(-1);

const ETAT_COMPLET = {
  parking: 'P1',
  places: [
    { id: 'A1', type: 'pmr', etat: 'libre', depuis: '2026-11-20T08:00:00Z', duree_max_min: 120 },
    { id: 'A3', type: 'normale', etat: 'libre', depuis: '2026-11-20T10:00:00Z', duree_max_min: 120 },
    { id: 'B4', type: 'electrique', etat: 'reservee', depuis: '2026-11-20T09:58:40Z', duree_max_min: 60 },
  ],
};
const ok = (corps = ETAT_COMPLET) => ({ statut: 200, corps: structuredClone(corps) });
const attendre = () => new Promise((r) => setTimeout(r, 0)); // laisse finir les await internes

function monter(surcharges = {}) {
  FauxWebSocket.instances = [];
  const journal = { changements: [], statuts: [], autres: [], expirations: 0, chargements: 0, minuteries: [] };
  const reponses = surcharges.reponses || [];
  const tr = demarrerTempsReel({
    WebSocket: FauxWebSocket,
    lire: () => ('jeton' in surcharges ? surcharges.jeton : 'jeton-de-test'),
    url: (j) => `ws://test/api/ws/places?token=${j}`,
    chargerPlaces: async () => { journal.chargements += 1; return reponses.length ? reponses.shift() : ok(); },
    sessionExpiree: () => { journal.expirations += 1; },
    enLigne: () => surcharges.enLigne ?? true,
    minuterie: {
      poser: (f, ms) => { journal.minuteries.push({ f, ms }); return journal.minuteries.length; },
      annuler: () => {},
    },
    surChangement: (place, etat) => journal.changements.push(`${place}:${etat}`),
    surStatut: (s, d) => journal.statuts.push(d.delai ? `${s}(${d.delai})` : s),
    surAutreMessage: (m) => journal.autres.push(m.type),
  });
  const tickMinuterie = () => journal.minuteries.at(-1).f();
  return { tr, journal, tickMinuterie };
}

// ---------- Tests ----------
const tests = [];
const test = (nom, f) => tests.push([nom, f]);

test('URL du WebSocket : wss en HTTPS, ws en HTTP, jeton encodé', () => {
  assert.equal(urlWebSocket('a.b c', { protocol: 'https:', host: 'exemple.tn' }),
    'wss://exemple.tn/api/ws/places?token=a.b%20c');
  assert.equal(urlWebSocket('j', { protocol: 'http:', host: 'localhost:8000' }),
    'ws://localhost:8000/api/ws/places?token=j');
});

test("démarrage : WebSocket avec le jeton, puis état complet, puis statut « connecte »", async () => {
  const { tr, journal } = monter();
  assert.equal(dernier().url, 'ws://test/api/ws/places?token=jeton-de-test');
  assert.deepEqual(journal.statuts, ['connexion']);
  dernier().ouvrir();
  await attendre();
  assert.equal(journal.chargements, 1);
  assert.deepEqual(journal.changements, ['A1:libre', 'A3:libre', 'B4:reservee']);
  assert.equal(tr.statut, STATUTS.CONNECTE);
  assert.equal(tr.places.get('B4').type, 'electrique');
});

test('un message { place, etat, depuis } met à jour la place', async () => {
  const { tr, journal } = monter();
  dernier().ouvrir();
  await attendre();
  dernier().recevoir({ place: 'A3', etat: 'occupee', depuis: '2026-11-20T10:02:11Z' });
  assert.equal(journal.changements.at(-1), 'A3:occupee');
  assert.equal(tr.places.get('A3').etat, 'occupee');
  assert.equal(tr.places.get('A3').depuis, '2026-11-20T10:02:11Z');
  assert.equal(tr.places.get('A3').type, 'normale'); // les autres champs sont gardés
});

test('messages ignorés : JSON invalide, état inconnu, place invalide, message plus ancien', async () => {
  const { tr, journal } = monter();
  dernier().ouvrir();
  await attendre();
  const avant = journal.changements.length;
  dernier().recevoir('pas du JSON');
  dernier().recevoir({ place: 'A3', etat: 'garee', depuis: '2026-11-20T10:05:00Z' });
  dernier().recevoir({ place: 'A3"]', etat: 'occupee' });
  dernier().recevoir({ place: 'A3', etat: 'occupee', depuis: '2026-11-20T09:00:00Z' }); // avant 10:00
  assert.equal(journal.changements.length, avant);
  assert.equal(tr.places.get('A3').etat, 'libre');
});

test('message avec « type » (événement, statut du Pi) : transmis à surAutreMessage', async () => {
  const { journal } = monter();
  dernier().ouvrir();
  await attendre();
  dernier().recevoir({ type: 'evenement', evenement: { type: 'cheval', places: ['A3', 'A4'] } });
  assert.deepEqual(journal.autres, ['evenement']);
});

test("messages reçus pendant le chargement de l'état complet : appliqués après lui", async () => {
  const { tr, journal } = monter();
  dernier().ouvrir(); // chargement en cours (await)
  dernier().recevoir({ place: 'A3', etat: 'occupee', depuis: '2026-11-20T10:02:11Z' }); // plus récent
  dernier().recevoir({ place: 'A1', etat: 'occupee', depuis: '2026-11-20T07:00:00Z' }); // plus ancien : ignoré
  await attendre();
  assert.deepEqual(journal.changements, ['A1:libre', 'A3:libre', 'B4:reservee', 'A3:occupee']);
  assert.equal(tr.places.get('A1').etat, 'libre');
});

test('coupure : reconnexion avec délai croissant (1 s, 2 s, 5 s, 10 s, 10 s)', async () => {
  const { journal, tickMinuterie } = monter();
  dernier().ouvrir();
  await attendre();
  dernier().couper(); // le serveur s'arrête
  await attendre();
  for (let i = 0; i < 4; i += 1) {
    tickMinuterie(); // nouvelle tentative…
    dernier().couper(); // …refusée (serveur toujours arrêté)
    await attendre();
  }
  assert.deepEqual(journal.minuteries.map((m) => m.ms), [1000, 2000, 5000, 10000, 10000]);
  assert.deepEqual(DELAIS_RECONNEXION_MS, [1000, 2000, 5000, 10000]);
  assert.ok(journal.statuts.includes('reconnexion(1000)'));
  assert.equal(FauxWebSocket.instances.length, 5);
});

test("reconnexion réussie : l'état complet est rechargé et le délai repart à 1 s", async () => {
  const etatApres = structuredClone(ETAT_COMPLET);
  etatApres.places[0].etat = 'occupee'; // A1 a changé pendant la coupure
  etatApres.places[0].depuis = '2026-11-20T10:30:00Z';
  const { tr, journal, tickMinuterie } = monter({ reponses: [ok(), ok(), ok(etatApres)] });
  dernier().ouvrir();
  await attendre();
  dernier().couper();
  await attendre();
  tickMinuterie();
  dernier().couper(); // 1re tentative refusée (2e chargement : vérification par l'API REST)
  await attendre();
  tickMinuterie();
  dernier().ouvrir(); // 2e tentative réussie : 3e chargement
  await attendre();
  assert.equal(journal.chargements, 3);
  assert.equal(tr.places.get('A1').etat, 'occupee');
  assert.equal(tr.statut, STATUTS.CONNECTE);
  dernier().couper();
  await attendre();
  assert.equal(journal.minuteries.at(-1).ms, 1000);
});

test('401 sur GET /api/places : session expirée, plus de reconnexion', async () => {
  const { tr, journal } = monter({ reponses: [{ statut: 401, corps: { erreur: 'non_authentifie' } }] });
  dernier().ouvrir();
  await attendre();
  assert.equal(journal.expirations, 1);
  assert.equal(dernier().ferme, true);
  assert.equal(journal.minuteries.length, 0);
  assert.equal(tr.statut, STATUTS.HORS_LIGNE);
});

test('poignée de main refusée puis 401 de l\'API : session expirée', async () => {
  const { journal } = monter({ reponses: [{ statut: 401, corps: null }] });
  dernier().couper(); // refus avant l'ouverture : le navigateur ne donne pas le code HTTP
  await attendre();
  assert.equal(journal.expirations, 1);
  assert.equal(journal.minuteries.length, 0);
});

test('fermeture 4401 par le serveur : session expirée', async () => {
  const { journal } = monter();
  dernier().ouvrir();
  await attendre();
  dernier().couper(4401);
  await attendre();
  assert.equal(journal.expirations, 1);
  assert.equal(journal.minuteries.length, 0);
});

test('pas de jeton : retour à la connexion, aucun WebSocket', () => {
  const { journal } = monter({ jeton: null });
  assert.equal(FauxWebSocket.instances.length, 0);
  assert.equal(journal.expirations, 1);
});

test('serveur injoignable (API REST : statut 0) : statut « hors-ligne »', async () => {
  const { journal } = monter({ reponses: [{ statut: 0, corps: null }] });
  dernier().couper();
  await attendre();
  assert.equal(journal.statuts.at(-1), 'hors-ligne(1000)');
});

test('arreter() : fermeture propre, pas de reconnexion', async () => {
  const { tr, journal } = monter();
  dernier().ouvrir();
  await attendre();
  tr.arreter();
  await attendre();
  assert.equal(dernier().ferme, true);
  assert.equal(journal.minuteries.length, 0);
});

test('colorerPlace : une seule classe d\'état sur [data-place]', () => {
  const classes = new Set(['libre']);
  const element = {
    classList: { remove: (...c) => c.forEach((x) => classes.delete(x)), add: (c) => classes.add(c) },
    setAttribute() {},
  };
  const racine = { querySelector: (s) => (s === '[data-place="A3"]' ? element : null) };
  assert.equal(colorerPlace(racine, 'A3', 'occupee'), true);
  assert.deepEqual([...classes], ['occupee']);
  assert.equal(colorerPlace(racine, 'Z9', 'libre'), false);
  assert.equal(colorerPlace(racine, 'A3', 'garee'), false);
});

let echecs = 0;
for (const [nom, f] of tests) {
  try {
    await f();
    console.log(`ok   ${nom}`);
  } catch (e) {
    echecs += 1;
    console.log(`ÉCHEC ${nom}\n     ${e.message.split('\n').join('\n     ')}`);
  }
}
console.log(`\n${tests.length - echecs}/${tests.length} tests réussis`);
process.exit(echecs ? 1 : 0);
