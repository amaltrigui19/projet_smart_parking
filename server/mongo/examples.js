// Documents d'exemple du schéma 2 (sprint 4) : conducteurs, autorisations, réservations, événements.
// À lancer APRÈS seed.js (places A1…B4) et index.js. Relançable : rien n'est dupliqué
// (identifiants fixes, upsert), et les dates sont recalculées par rapport à maintenant.
//
// LOCAL SEULEMENT : les comptes d'exemple ont un mot de passe connu. Ne pas lancer sur la VM.
//
// Usage : ./server/scripts/run-mongo.sh server/mongo/examples.js

// Mot de passe de test des deux conducteurs d'exemple (connexion possible avec /api/auth/login).
const EXAMPLE_PASSWORD = "Exemple-2026";
// Empreinte Argon2id précalculée de EXAMPLE_PASSWORD (format de middleware/iam Argon2Utility,
// fournie et vérifiée par Maissa dans la PR #24). Le mot de passe d'exemple est fixe et public.
const EXAMPLE_HASH = "$argon2id$v=19$m=19456,t=2,p=1$/9wbK/2DH2M2gycW/yA/yg$f7Yy8M6zT2zt9VflgQZildTij3cMs+FzcDPeP0c54cM";

const now = new Date();
const minutesAgo = (minutes) => new Date(now.getTime() - minutes * 60_000);
const inMinutes = (minutes) => new Date(now.getTime() + minutes * 60_000);

// ---------- Utilisateurs ----------
// Un compte existant (même email) n'est pas modifié : on réutilise son _id.
function driver(name, email) {
  const existing = db.users.findOne({ email });
  if (existing) return existing._id;
  const _id = new ObjectId();
  db.users.insertOne({
    _id, name, email, password_hash: EXAMPLE_HASH,
    role: "DRIVER", active: true, created_at: minutesAgo(7 * 24 * 60),
  });
  return _id;
}

const sara = driver("Sara Ben Ali", "sara@exemple.tn");
const youssef = driver("Youssef Trabelsi", "youssef@exemple.tn");

// L'opérateur de démo (créé par WildFly depuis server/.env) s'il existe ; sinon null.
const operator = db.users.findOne({ role: "OPERATOR" }, { _id: 1 });
const operatorId = operator ? operator._id : null;

// ---------- Autorisations ----------
db.permissions.replaceOne(
  { _id: ObjectId("64e0000000000000000a0001") },
  { user: sara, type: "accessible", start: minutesAgo(7 * 24 * 60), end: null, granted_by: operatorId },
  { upsert: true }
);

// ---------- Réservations ----------
// Une seule réservation ouverte (active / in_progress) par conducteur et par place : voir index.js.
const reservations = [
  { // payée : 1 h 19 sur place → 1 h 30 facturée à 1,5 TND/h
    _id: ObjectId("64e0000000000000000b0001"), user: sara, spot: "B2", state: "paid",
    created_at: minutesAgo(2 * 24 * 60 + 85), expires_at: minutesAgo(2 * 24 * 60 + 70),
    start: minutesAgo(2 * 24 * 60 + 82), end: minutesAgo(2 * 24 * 60 + 3), amount: 2.25, currency: "TND",
    payment: { method: "simulated", paid_at: minutesAgo(2 * 24 * 60 + 2), receipt: "RECU-2026-000001" },
  },
  { // annulée par le conducteur
    _id: ObjectId("64e0000000000000000b0002"), user: youssef, spot: "A3", state: "cancelled",
    created_at: minutesAgo(26 * 60), expires_at: minutesAgo(26 * 60 - 15),
    start: null, end: null, amount: null, currency: null, payment: null,
  },
  { // expirée : 15 min sans arrivée
    _id: ObjectId("64e0000000000000000b0003"), user: youssef, spot: "A2", state: "expired",
    created_at: minutesAgo(25 * 60), expires_at: minutesAgo(25 * 60 - 15),
    start: null, end: null, amount: null, currency: null, payment: null,
  },
  { // active en ce moment : B3 apparaît « réservée » sur le plan
    _id: ObjectId("64e0000000000000000b0004"), user: sara, spot: "B3", state: "active",
    created_at: minutesAgo(2), expires_at: inMinutes(13),
    start: null, end: null, amount: null, currency: null, payment: null,
  },
];

for (const r of reservations) {
  try {
    db.reservations.replaceOne({ _id: r._id }, r, { upsert: true });
  } catch (e) {
    // index unique partiel : une autre réservation ouverte existe déjà (créée depuis l'API)
    print(`[ATTENTION] réservation ${r.spot} (${r.state}) non écrite : ${e.message}`);
  }
}

// ---------- Événements ----------
const events = [
  {
    _id: ObjectId("64e0000000000000000c0001"), parking: "P1", type: "straddling", spots: ["A3", "A4"], zone: null,
    start: minutesAgo(5), end: null, source: "pi", details: null,
    handled: false, handled_by: null, handled_at: null, comment: null,
  },
  {
    _id: ObjectId("64e0000000000000000c0002"), parking: "P1", type: "overstay", spots: ["A2"], zone: null,
    start: minutesAgo(3 * 60), end: minutesAgo(2 * 60), source: "server", details: { duration_min: 121, max_duration_min: 120 },
    handled: true, handled_by: operatorId, handled_at: minutesAgo(150), comment: "Conducteur prévenu",
  },
  {
    _id: ObjectId("64e0000000000000000c0003"), parking: "P1", type: "forbidden_zone", spots: [], zone: "allee1",
    start: minutesAgo(40), end: minutesAgo(32), source: "pi", details: null,
    handled: false, handled_by: null, handled_at: null, comment: null,
  },
  {
    _id: ObjectId("64e0000000000000000c0004"), parking: "P1", type: "unauthorized_special_spot", spots: ["A1"], zone: null,
    start: minutesAgo(12), end: null, source: "server", details: { spot_type: "accessible" },
    handled: false, handled_by: null, handled_at: null, comment: null,
  },
];

for (const e of events) {
  db.events.replaceOne({ _id: e._id }, e, { upsert: true });
}

print(
  `Exemples chargés : ${db.users.countDocuments({ role: "DRIVER" })} driver(s), ` +
  `${db.permissions.countDocuments()} autorisation(s), ${db.reservations.countDocuments()} réservation(s), ` +
  `${db.events.countDocuments()} événement(s).`
);
if (!operatorId) {
  print("(aucun opérateur en base : granted_by et handled_by restent null ; démarrer WildFly avec OPERATOR_EMAIL pour le créer)");
}
