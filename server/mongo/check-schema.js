// Vérifie le schéma 2 (sprint 4) : les requêtes de l'API utilisent bien les index,
// et la base refuse une deuxième réservation ouverte. À lancer après index.js et examples.js.
// Ne laisse aucune trace : les documents de test sont supprimés à la fin.
//
// Usage : ./server/scripts/run-mongo.sh server/mongo/check-schema.js

let ok = 0;
let failures = 0;
function check(description, condition, detail = "") {
  if (condition) { ok++; print(`[OK]    ${description}`); }
  else { failures++; print(`[ECHEC] ${description}${detail ? " : " + detail : ""}`); }
}

// Nom de l'index choisi par MongoDB pour une requête (cherche « indexName » dans le plan gagnant).
function usedIndex(curseur) {
  const plan = curseur.explain("queryPlanner").queryPlanner.winningPlan;
  const find = (o) => {
    if (!o || typeof o !== "object") return null;
    if (typeof o.indexName === "string") return o.indexName;
    for (const v of Object.values(o)) {
      const n = find(v);
      if (n) return n;
    }
    return null;
  };
  return find(plan) || "aucun (COLLSCAN)";
}

// ---------- 1. Index présents ----------
const expected = {
  permissions: ["user_type"],
  reservations: ["user_created_at", "spot_state", "user_open_unique", "spot_open_unique"],
  events: ["handled_start", "type_start"],
  users: ["email_unique"],
};
for (const [c, names] of Object.entries(expected)) {
  const present = db.getCollection(c).getIndexes().map((i) => i.name);
  for (const n of names) check(`index ${c}.${n} présent`, present.includes(n), "lancer index.js");
}

// ---------- 2. Les requêtes de l'API utilisent les index ----------
const sara = db.users.findOne({ email: "sara@exemple.tn" });
if (!sara) {
  print("Pas de conducteur d'exemple : lancer d'abord examples.js.");
  quit(1);
}
const u = sara._id;
const queries = [
  ["GET /api/reservations (mon historique)", db.reservations.find({ user: u }).sort({ created_at: -1 }).limit(20), "user_created_at"],
  ["réservation ouverte d'une place (plan)", db.reservations.find({ spot: "B3", state: { $in: ["active", "in_progress"] } }), ["spot_state", "spot_open_unique"]],
  ["autorisation accessible d'un conducteur", db.permissions.find({ user: u, type: "accessible" }), "user_type"],
  ["GET /api/events?handled=false", db.events.find({ handled: false }).sort({ start: -1 }).limit(50), "handled_start"],
  ["GET /api/events?type=straddling&from=…", db.events.find({ type: "straddling", start: { $gte: new Date(Date.now() - 86400000) } }).sort({ start: -1 }), "type_start"],
];
for (const [description, curseur, index] of queries) {
  const used = usedIndex(curseur);
  const accepted = Array.isArray(index) ? index : [index];
  check(`${description} → ${used}`, accepted.includes(used), `attendu ${accepted.join(" ou ")}`);
}

// ---------- 3. Une seule réservation ouverte par conducteur et par place ----------
const ID_TEST = [ObjectId("64e0000000000000000bff01"), ObjectId("64e0000000000000000bff02")];
db.reservations.deleteMany({ _id: { $in: ID_TEST } });
const now = new Date();
const base = { created_at: now, expires_at: new Date(now.getTime() + 15 * 60000), start: null, end: null,
  amount: null, currency: null, payment: null };

const saraOpen = db.reservations.findOne({ user: u, state: { $in: ["active", "in_progress"] } });
check("Sara a une réservation ouverte (examples.js)", saraOpen !== null);

function rejected(doc, index) {
  try {
    db.reservations.insertOne(doc);
    return false;
  } catch (e) {
    return e.code === 11000 && e.message.includes(index);
  }
}
check("2e réservation active du même conducteur refusée (user_open_unique)",
  rejected({ _id: ID_TEST[0], user: u, spot: "A4", state: "active", ...base }, "user_open_unique"));
check("réservation active sur une place déjà réservée refusée (spot_open_unique)",
  rejected({ _id: ID_TEST[1], user: new ObjectId(), spot: saraOpen ? saraOpen.spot : "B3", state: "active", ...base }, "spot_open_unique"));

let acceptedTwice = true;
try {
  db.reservations.insertOne({ _id: ID_TEST[0], user: u, spot: "A4", state: "completed", ...base });
} catch (e) { acceptedTwice = false; }
check("une réservation terminée du même conducteur reste acceptée (index partiel)", acceptedTwice);

db.reservations.deleteMany({ _id: { $in: ID_TEST } });

print(`\n${ok} vérification(s) réussie(s), ${failures} en échec`);
if (failures > 0) quit(1);
