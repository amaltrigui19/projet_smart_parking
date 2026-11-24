// Tests of frontend/back-office/js/spot-validation.js (rules of the 400 invalid_config error, contract §2.10).
// Run from the root of the repository: node frontend/tools/test_spot_validation.js
"use strict";

const assert = require("node:assert/strict");
const { validateSpots } = require("../back-office/js/spot-validation.js");

const PLAN = ["A1", "A2", "A3", "A4", "B1", "B2", "B3", "B4"];
const valid = () => ({
  parking: "P1",
  resolution: [640, 480],
  spots: [
    { id: "A1", type: "accessible", max_duration_min: 120, polygon: [[40, 60], [130, 60], [130, 200], [40, 200]] },
    { id: "B4", type: "electric", max_duration_min: 60, polygon: [[340, 280], [430, 280], [430, 420], [340, 420]] },
  ],
  forbidden_zones: [{ id: "allee1", polygon: [[500, 210], [639, 210], [639, 280], [500, 280]] }],
});

const cases = [
  ["fichier valide", (d) => d, null],
  ["identifiant en double", (d) => { d.spots[1].id = "A1"; }, "en double"],
  ["double entre place et zone", (d) => { d.forbidden_zones[0].id = "A1"; }, "en double"],
  ["polygone de 2 points", (d) => { d.spots[0].polygon = [[1, 1], [5, 5]]; }, "au moins 3 points"],
  ["point hors de l'image", (d) => { d.spots[0].polygon[1] = [640, 60]; }, "sort de l'image"],
  ["point non entier", (d) => { d.spots[0].polygon[1] = [130.5, 60]; }, "pixels entiers"],
  ["polygone plat", (d) => { d.spots[0].polygon = [[1, 1], [2, 2], [3, 3]]; }, "plat"],
  ["type inconnu", (d) => { d.spots[0].type = "reserved"; }, "inconnu"],
  ["ancien nom de type (pmr)", (d) => { d.spots[0].type = "pmr"; }, "inconnu"],
  ["durée nulle", (d) => { d.spots[0].max_duration_min = 0; }, "durée maximale"],
  ["durée négative", (d) => { d.spots[0].max_duration_min = -5; }, "durée maximale"],
  ["place absente du plan", (d) => { d.spots[0].id = "P1"; }, "n'existe pas dans le plan"],
  ["résolution manquante", (d) => { delete d.resolution; }, "'resolution'"],
  ["identifiant avec accent", (d) => { d.spots[0].id = "é1"; }, "identifiant manquant ou invalide"],
  ["ancien format (places)", (d) => { d.places = d.spots; delete d.spots; }, "'spots'"],
];

let failures = 0;
for (const [name, change, expected] of cases) {
  const d = valid();
  change(d);
  const errors = validateSpots(d, PLAN);
  try {
    if (expected === null) assert.deepEqual(errors, []);
    else assert.ok(errors.some((e) => e.includes(expected)), `attendu « ${expected} », obtenu ${JSON.stringify(errors)}`);
    console.log(`[OK]    ${name}`);
  } catch (e) {
    failures++;
    console.log(`[ECHEC] ${name} : ${e.message}`);
  }
}
console.log(failures ? `${failures} test(s) en échec` : `${cases.length} tests réussis`);
process.exit(failures ? 1 : 0);
