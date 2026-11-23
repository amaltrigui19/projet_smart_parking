// Vérifie le schéma 2 (sprint 4) : les requêtes de l'API utilisent bien les index,
// et la base refuse une deuxième réservation ouverte. À lancer après index.js et exemples.js.
// Ne laisse aucune trace : les documents de test sont supprimés à la fin.
//
// Usage : ./server/scripts/executer-mongo.sh server/mongo/verifier-schema.js

let ok = 0;
let echecs = 0;
function verifier(description, condition, detail = "") {
  if (condition) { ok++; print(`[OK]    ${description}`); }
  else { echecs++; print(`[ECHEC] ${description}${detail ? " : " + detail : ""}`); }
}

// Nom de l'index choisi par MongoDB pour une requête (cherche « indexName » dans le plan gagnant).
function indexUtilise(curseur) {
  const plan = curseur.explain("queryPlanner").queryPlanner.winningPlan;
  const trouver = (o) => {
    if (!o || typeof o !== "object") return null;
    if (typeof o.indexName === "string") return o.indexName;
    for (const v of Object.values(o)) {
      const n = trouver(v);
      if (n) return n;
    }
    return null;
  };
  return trouver(plan) || "aucun (COLLSCAN)";
}

// ---------- 1. Index présents ----------
const attendus = {
  permissions: ["user_type"],
  reservations: ["user_created_at", "spot_state", "user_open_unique", "spot_open_unique"],
  events: ["handled_start", "type_start"],
  users: ["email_unique"],
};
for (const [c, noms] of Object.entries(attendus)) {
  const presents = db.getCollection(c).getIndexes().map((i) => i.name);
  for (const n of noms) verifier(`index ${c}.${n} présent`, presents.includes(n), "lancer index.js");
}

// ---------- 2. Les requêtes de l'API utilisent les index ----------
const sara = db.users.findOne({ email: "sara@exemple.tn" });
if (!sara) {
  print("Pas de conducteur d'exemple : lancer d'abord exemples.js.");
  quit(1);
}
const u = sara._id;
const requetes = [
  ["GET /api/reservations (mon historique)", db.reservations.find({ user: u }).sort({ created_at: -1 }).limit(20), "user_created_at"],
  ["réservation ouverte d'une place (plan)", db.reservations.find({ spot: "B3", state: { $in: ["active", "in_progress"] } }), ["spot_state", "spot_open_unique"]],
  ["autorisation accessible d'un conducteur", db.permissions.find({ user: u, type: "accessible" }), "user_type"],
  ["GET /api/events?handled=false", db.events.find({ handled: false }).sort({ start: -1 }).limit(50), "handled_start"],
  ["GET /api/events?type=straddling&from=…", db.events.find({ type: "straddling", start: { $gte: new Date(Date.now() - 86400000) } }).sort({ start: -1 }), "type_start"],
];
for (const [description, curseur, index] of requetes) {
  const utilise = indexUtilise(curseur);
  const acceptes = Array.isArray(index) ? index : [index];
  verifier(`${description} → ${utilise}`, acceptes.includes(utilise), `attendu ${acceptes.join(" ou ")}`);
}

// ---------- 3. Une seule réservation ouverte par conducteur et par place ----------
const ID_TEST = [ObjectId("64e0000000000000000bff01"), ObjectId("64e0000000000000000bff02")];
db.reservations.deleteMany({ _id: { $in: ID_TEST } });
const maintenant = new Date();
const base = { created_at: maintenant, expires_at: new Date(maintenant.getTime() + 15 * 60000), start: null, end: null,
  amount: null, currency: null, payment: null };

const ouverteSara = db.reservations.findOne({ user: u, state: { $in: ["active", "in_progress"] } });
verifier("Sara a une réservation ouverte (exemples.js)", ouverteSara !== null);

function refusee(doc, index) {
  try {
    db.reservations.insertOne(doc);
    return false;
  } catch (e) {
    return e.code === 11000 && e.message.includes(index);
  }
}
verifier("2e réservation active du même conducteur refusée (user_open_unique)",
  refusee({ _id: ID_TEST[0], user: u, spot: "A4", state: "active", ...base }, "user_open_unique"));
verifier("réservation active sur une place déjà réservée refusée (spot_open_unique)",
  refusee({ _id: ID_TEST[1], user: new ObjectId(), spot: ouverteSara ? ouverteSara.spot : "B3", state: "active", ...base }, "spot_open_unique"));

let accepte = true;
try {
  db.reservations.insertOne({ _id: ID_TEST[0], user: u, spot: "A4", state: "completed", ...base });
} catch (e) { accepte = false; }
verifier("une réservation terminée du même conducteur reste acceptée (index partiel)", accepte);

db.reservations.deleteMany({ _id: { $in: ID_TEST } });

print(`\n${ok} vérification(s) réussie(s), ${echecs} en échec`);
if (echecs > 0) quit(1);
