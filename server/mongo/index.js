// Index de la base smartparking (contrat §2.14), schéma 2 : sprint 4.
// Relançable sans risque : createIndex ne fait rien si l'index existe déjà avec les mêmes options.
// Les noms et options sont IDENTIQUES à ceux des DAO Java (middleware/core et middleware/iam) :
// si on change l'un, il faut changer l'autre, sinon MongoDB refuse avec « IndexOptionsConflict ».
//
// Usage : ./server/scripts/executer-mongo.sh server/mongo/index.js

// Une réservation est « ouverte » tant qu'elle est active ou en_cours (contrat §2.8).
const OUVERTES = { etat: { $in: ["active", "en_cours"] } };

const INDEX = {
  // Sprint 3 (rappel, déjà créés par seed.js et par les DAO)
  places: [
    [{ parking: 1 }, { name: "parking" }],
  ],
  utilisateurs: [
    [{ email: 1 }, { name: "email_unique", unique: true }],
  ],

  // Sprint 4
  autorisations: [
    // « ce conducteur a-t-il une autorisation pmr / electrique ? »
    [{ utilisateur: 1, type: 1 }, { name: "utilisateur_type" }],
  ],
  reservations: [
    // historique d'un conducteur, du plus récent au plus ancien
    [{ utilisateur: 1, creee_le: -1 }, { name: "utilisateur_creee_le" }],
    // réservations d'une place dans un état donné (plan : place réservée)
    [{ place: 1, etat: 1 }, { name: "place_etat" }],
    // une seule réservation ouverte par conducteur (409 reservation_deja_active)
    [{ utilisateur: 1 }, { name: "utilisateur_ouverte_unique", unique: true, partialFilterExpression: OUVERTES }],
    // une seule réservation ouverte par place (409 place_indisponible)
    [{ place: 1 }, { name: "place_ouverte_unique", unique: true, partialFilterExpression: OUVERTES }],
  ],
  evenements: [
    // alertes non traitées, de la plus récente à la plus ancienne
    [{ traite: 1, debut: -1 }, { name: "traite_debut" }],
    // filtre par type et par date (GET /api/evenements?type=…&du=…&au=…)
    [{ type: 1, debut: -1 }, { name: "type_debut" }],
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
