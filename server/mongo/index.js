// Index de la base smartparking (contrat §2.14), schéma 2 : sprint 4.
// Relançable sans risque : createIndex ne fait rien si l'index existe déjà avec les mêmes options.
// Les noms et options sont IDENTIQUES à ceux des DAO Java (middleware/core et middleware/iam) :
// si on change l'un, il faut changer l'autre, sinon MongoDB refuse avec « IndexOptionsConflict ».
//
// Usage : ./server/scripts/executer-mongo.sh server/mongo/index.js

// Une réservation est « ouverte » tant qu'elle est active ou in_progress (contrat §2.8).
const OUVERTES = { state: { $in: ["active", "in_progress"] } };

const INDEX = {
  // Sprint 3 (rappel, déjà créés par seed.js et par les DAO)
  spots: [
    [{ parking: 1 }, { name: "parking" }],
  ],
  users: [
    [{ email: 1 }, { name: "email_unique", unique: true }],
  ],

  // Sprint 4
  permissions: [
    // « ce conducteur a-t-il une autorisation pmr / electrique ? »
    [{ user: 1, type: 1 }, { name: "user_type" }],
  ],
  reservations: [
    // historique d'un conducteur, du plus récent au plus ancien
    [{ user: 1, created_at: -1 }, { name: "user_created_at" }],
    // réservations d'une place dans un état donné (plan : place réservée)
    [{ spot: 1, state: 1 }, { name: "spot_state" }],
    // une seule réservation ouverte par conducteur (409 reservation_already_active)
    [{ user: 1 }, { name: "user_open_unique", unique: true, partialFilterExpression: OUVERTES }],
    // une seule réservation ouverte par place (409 spot_unavailable)
    [{ spot: 1 }, { name: "spot_open_unique", unique: true, partialFilterExpression: OUVERTES }],
  ],
  events: [
    // alertes non traitées, de la plus récente à la plus ancienne
    [{ handled: 1, start: -1 }, { name: "handled_start" }],
    // filtre par type et par date (GET /api/events?type=…&from=…&to=…)
    [{ type: 1, start: -1 }, { name: "type_start" }],
  ],
};

for (const [collection, liste] of Object.entries(INDEX)) {
  for (const [cles, options] of liste) {
    db.getCollection(collection).createIndex(cles, options);
  }
  const noms = db.getCollection(collection).getIndexes().map((i) => i.name).filter((n) => n !== "_id_");
  print(`${collection.padEnd(14)} ${noms.join(", ")}`);
}

print("Index créés.");
