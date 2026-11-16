// Tests de frontend/js/validation-places.js (règles de l'erreur 400 config_invalide, contrat §2.10).
// Lancer depuis la racine du dépôt : node frontend/outils/test_validation.js
"use strict";

const assert = require("node:assert/strict");
const { validerPlaces } = require("../js/validation-places.js");

const PLAN = ["A1", "A2", "A3", "A4", "B1", "B2", "B3", "B4"];
const valide = () => ({
  parking: "P1",
  resolution: [640, 480],
  places: [
    { id: "A1", type: "pmr", duree_max_min: 120, poly: [[40, 60], [130, 60], [130, 200], [40, 200]] },
    { id: "B4", type: "electrique", duree_max_min: 60, poly: [[340, 280], [430, 280], [430, 420], [340, 420]] },
  ],
  zones_interdites: [{ id: "allee1", poly: [[500, 210], [639, 210], [639, 280], [500, 280]] }],
});

const cas = [
  ["fichier valide", (d) => d, null],
  ["identifiant en double", (d) => { d.places[1].id = "A1"; }, "en double"],
  ["double entre place et zone", (d) => { d.zones_interdites[0].id = "A1"; }, "en double"],
  ["polygone de 2 points", (d) => { d.places[0].poly = [[1, 1], [5, 5]]; }, "au moins 3 points"],
  ["point hors de l'image", (d) => { d.places[0].poly[1] = [640, 60]; }, "sort de l'image"],
  ["point non entier", (d) => { d.places[0].poly[1] = [130.5, 60]; }, "pixels entiers"],
  ["polygone plat", (d) => { d.places[0].poly = [[1, 1], [2, 2], [3, 3]]; }, "plat"],
  ["type inconnu", (d) => { d.places[0].type = "reservee"; }, "inconnu"],
  ["durée nulle", (d) => { d.places[0].duree_max_min = 0; }, "durée maximale"],
  ["durée négative", (d) => { d.places[0].duree_max_min = -5; }, "durée maximale"],
  ["place absente du plan", (d) => { d.places[0].id = "P1"; }, "n'existe pas dans le plan"],
  ["résolution manquante", (d) => { delete d.resolution; }, "'resolution'"],
  ["identifiant avec accent", (d) => { d.places[0].id = "é1"; }, "identifiant manquant ou invalide"],
];

let echecs = 0;
for (const [nom, modifier, attendu] of cas) {
  const d = valide();
  modifier(d);
  const erreurs = validerPlaces(d, PLAN);
  try {
    if (attendu === null) assert.deepEqual(erreurs, []);
    else assert.ok(erreurs.some((e) => e.includes(attendu)), `attendu « ${attendu} », obtenu ${JSON.stringify(erreurs)}`);
    console.log(`[OK]    ${nom}`);
  } catch (e) {
    echecs++;
    console.log(`[ECHEC] ${nom} : ${e.message}`);
  }
}
console.log(echecs ? `${echecs} test(s) en échec` : `${cas.length} tests réussis`);
process.exit(echecs ? 1 : 0);
