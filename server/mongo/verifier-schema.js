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
  autorisations: ["utilisateur_type"],
  reservations: ["utilisateur_creee_le", "place_etat", "utilisateur_ouverte_unique", "place_ouverte_unique"],
  evenements: ["traite_debut", "type_debut"],
  utilisateurs: ["email_unique"],
};
for (const [c, noms] of Object.entries(attendus)) {
  const presents = db.getCollection(c).getIndexes().map((i) => i.name);
  for (const n of noms) verifier(`index ${c}.${n} présent`, presents.includes(n), "lancer index.js");
}

// ---------- 2. Les requêtes de l'API utilisent les index ----------
const sara = db.utilisateurs.findOne({ email: "sara@exemple.tn" });
if (!sara) {
  print("Pas de conducteur d'exemple : lancer d'abord exemples.js.");
  quit(1);
}
const u = sara._id;
const requetes = [
  ["GET /api/reservations (mon historique)", db.reservations.find({ utilisateur: u }).sort({ creee_le: -1 }).limit(20), "utilisateur_creee_le"],
  ["réservation ouverte d'une place (plan)", db.reservations.find({ place: "B3", etat: { $in: ["active", "en_cours"] } }), ["place_etat", "place_ouverte_unique"]],
  ["autorisation pmr d'un conducteur", db.autorisations.find({ utilisateur: u, type: "pmr" }), "utilisateur_type"],
  ["GET /api/evenements?traite=false", db.evenements.find({ traite: false }).sort({ debut: -1 }).limit(50), "traite_debut"],
  ["GET /api/evenements?type=cheval&du=…", db.evenements.find({ type: "cheval", debut: { $gte: new Date(Date.now() - 86400000) } }).sort({ debut: -1 }), "type_debut"],
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
const base = { creee_le: maintenant, expire_le: new Date(maintenant.getTime() + 15 * 60000), debut: null, fin: null,
  montant: null, devise: null, paiement: null };

const ouverteSara = db.reservations.findOne({ utilisateur: u, etat: { $in: ["active", "en_cours"] } });
verifier("Sara a une réservation ouverte (exemples.js)", ouverteSara !== null);

function refusee(doc, index) {
  try {
    db.reservations.insertOne(doc);
    return false;
  } catch (e) {
    return e.code === 11000 && e.message.includes(index);
  }
}
verifier("2e réservation active du même conducteur refusée (utilisateur_ouverte_unique)",
  refusee({ _id: ID_TEST[0], utilisateur: u, place: "A4", etat: "active", ...base }, "utilisateur_ouverte_unique"));
verifier("réservation active sur une place déjà réservée refusée (place_ouverte_unique)",
  refusee({ _id: ID_TEST[1], utilisateur: new ObjectId(), place: ouverteSara ? ouverteSara.place : "B3", etat: "active", ...base }, "place_ouverte_unique"));

let accepte = true;
try {
  db.reservations.insertOne({ _id: ID_TEST[0], utilisateur: u, place: "A4", etat: "terminee", ...base });
} catch (e) { accepte = false; }
verifier("une réservation terminée du même conducteur reste acceptée (index partiel)", accepte);

db.reservations.deleteMany({ _id: { $in: ID_TEST } });

print(`\n${ok} vérification(s) réussie(s), ${echecs} en échec`);
if (echecs > 0) quit(1);
