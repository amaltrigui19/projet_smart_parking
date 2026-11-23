// Documents d'exemple du schéma 2 (sprint 4) : conducteurs, autorisations, réservations, événements.
// À lancer APRÈS seed.js (places A1…B4) et index.js. Relançable : rien n'est dupliqué
// (identifiants fixes, upsert), et les dates sont recalculées par rapport à maintenant.
//
// LOCAL SEULEMENT : les comptes d'exemple ont un mot de passe connu. Ne pas lancer sur la VM.
//
// Usage : ./server/scripts/executer-mongo.sh server/mongo/exemples.js

const crypto = require("crypto");

// Mot de passe de test des deux conducteurs d'exemple (connexion possible avec /api/auth/connexion).
const MOT_DE_PASSE_EXEMPLE = "Exemple-2026";

const maintenant = new Date();
const ilYa = (minutes) => new Date(maintenant.getTime() - minutes * 60_000);
const dans = (minutes) => new Date(maintenant.getTime() + minutes * 60_000);

// Même format que middleware/iam HachageMotDePasse : pbkdf2_sha256$<itérations>$<sel b64>$<empreinte b64>
function hacher(motDePasse) {
  const iterations = 600000;
  const sel = crypto.randomBytes(16);
  const empreinte = crypto.pbkdf2Sync(motDePasse, sel, iterations, 32, "sha256");
  return `pbkdf2_sha256$${iterations}$${sel.toString("base64")}$${empreinte.toString("base64")}`;
}

// ---------- Utilisateurs ----------
// Un compte existant (même email) n'est pas modifié : on réutilise son _id.
function conducteur(nom, email) {
  const existant = db.utilisateurs.findOne({ email });
  if (existant) return existant._id;
  const _id = new ObjectId();
  db.utilisateurs.insertOne({
    _id, nom, email, mot_de_passe_hash: hacher(MOT_DE_PASSE_EXEMPLE),
    role: "CONDUCTEUR", actif: true, cree_le: ilYa(7 * 24 * 60),
  });
  return _id;
}

const sara = conducteur("Sara Ben Ali", "sara@exemple.tn");
const youssef = conducteur("Youssef Trabelsi", "youssef@exemple.tn");

// L'exploitant de démo (créé par WildFly depuis server/.env) s'il existe ; sinon null.
const exploitant = db.utilisateurs.findOne({ role: "EXPLOITANT" }, { _id: 1 });
const exploitantId = exploitant ? exploitant._id : null;

// ---------- Autorisations ----------
db.autorisations.replaceOne(
  { _id: ObjectId("64e0000000000000000a0001") },
  { utilisateur: sara, type: "pmr", debut: ilYa(7 * 24 * 60), fin: null, accordee_par: exploitantId },
  { upsert: true }
);

// ---------- Réservations ----------
// Une seule réservation ouverte (active / en_cours) par conducteur et par place : voir index.js.
const reservations = [
  { // payée : 1 h 19 sur place → 1 h 30 facturée à 1,5 TND/h
    _id: ObjectId("64e0000000000000000b0001"), utilisateur: sara, place: "B2", etat: "payee",
    creee_le: ilYa(2 * 24 * 60 + 85), expire_le: ilYa(2 * 24 * 60 + 70),
    debut: ilYa(2 * 24 * 60 + 82), fin: ilYa(2 * 24 * 60 + 3), montant: 2.25, devise: "TND",
    paiement: { moyen: "simule", paye_le: ilYa(2 * 24 * 60 + 2), recu: "RECU-2026-000001" },
  },
  { // annulée par le conducteur
    _id: ObjectId("64e0000000000000000b0002"), utilisateur: youssef, place: "A3", etat: "annulee",
    creee_le: ilYa(26 * 60), expire_le: ilYa(26 * 60 - 15),
    debut: null, fin: null, montant: null, devise: null, paiement: null,
  },
  { // expirée : 15 min sans arrivée
    _id: ObjectId("64e0000000000000000b0003"), utilisateur: youssef, place: "A2", etat: "expiree",
    creee_le: ilYa(25 * 60), expire_le: ilYa(25 * 60 - 15),
    debut: null, fin: null, montant: null, devise: null, paiement: null,
  },
  { // active en ce moment : B3 apparaît « réservée » sur le plan
    _id: ObjectId("64e0000000000000000b0004"), utilisateur: sara, place: "B3", etat: "active",
    creee_le: ilYa(2), expire_le: dans(13),
    debut: null, fin: null, montant: null, devise: null, paiement: null,
  },
];

for (const r of reservations) {
  try {
    db.reservations.replaceOne({ _id: r._id }, r, { upsert: true });
  } catch (e) {
    // index unique partiel : une autre réservation ouverte existe déjà (créée depuis l'API)
    print(`[ATTENTION] réservation ${r.place} (${r.etat}) non écrite : ${e.message}`);
  }
}

// ---------- Événements ----------
const evenements = [
  {
    _id: ObjectId("64e0000000000000000c0001"), parking: "P1", type: "cheval", places: ["A3", "A4"], zone: null,
    debut: ilYa(5), fin: null, source: "pi", details: null,
    traite: false, traite_par: null, traite_le: null, commentaire: null,
  },
  {
    _id: ObjectId("64e0000000000000000c0002"), parking: "P1", type: "ventouse", places: ["A2"], zone: null,
    debut: ilYa(3 * 60), fin: ilYa(2 * 60), source: "serveur", details: { duree_min: 121, duree_max_min: 120 },
    traite: true, traite_par: exploitantId, traite_le: ilYa(150), commentaire: "Conducteur prévenu",
  },
  {
    _id: ObjectId("64e0000000000000000c0003"), parking: "P1", type: "zone_interdite", places: [], zone: "allee1",
    debut: ilYa(40), fin: ilYa(32), source: "pi", details: null,
    traite: false, traite_par: null, traite_le: null, commentaire: null,
  },
  {
    _id: ObjectId("64e0000000000000000c0004"), parking: "P1", type: "place_speciale", places: ["A1"], zone: null,
    debut: ilYa(12), fin: null, source: "serveur", details: { type_place: "pmr" },
    traite: false, traite_par: null, traite_le: null, commentaire: null,
  },
];

for (const e of evenements) {
  db.evenements.replaceOne({ _id: e._id }, e, { upsert: true });
}

print(
  `Exemples chargés : ${db.utilisateurs.countDocuments({ role: "CONDUCTEUR" })} conducteur(s), ` +
  `${db.autorisations.countDocuments()} autorisation(s), ${db.reservations.countDocuments()} réservation(s), ` +
  `${db.evenements.countDocuments()} événement(s).`
);
if (!exploitantId) {
  print("(aucun exploitant en base : accordee_par et traite_par restent null ; démarrer WildFly avec EXPLOITANT_EMAIL pour le créer)");
}
