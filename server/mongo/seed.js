// Seed : parking P1 de la maquette (contrat 4.1 et 4.3) et ses 8 places.
// Relançable sans risque : chaque document est remplacé (upsert), rien n'est dupliqué.

const RESOLUTION = [640, 480];      // image de la caméra, en pixels (contrat 5.2)
const DIMENSIONS_CM = [60, 40];     // maquette, en cm (contrat 4.3)
const ECHELLE_X = RESOLUTION[0] / DIMENSIONS_CM[0];   // pixels par cm
const ECHELLE_Y = RESOLUTION[1] / DIMENSIONS_CM[1];

// Polygone PROVISOIRE : le rectangle du plan converti en pixels, comme si la caméra était
// parfaitement au-dessus de la maquette. Il sera remplacé par la vraie calibration.
function polyDepuisRect([x, y, l, h]) {
  const px = (v) => Math.round(v * ECHELLE_X);
  const py = (v) => Math.round(v * ECHELLE_Y);
  return [[px(x), py(y)], [px(x + l), py(y)], [px(x + l), py(y + h)], [px(x), py(y + h)]];
}

const places = [
  { id: "A1", type: "pmr",        rect: [14, 0, 8, 13] },
  { id: "A2", type: "normale",    rect: [22, 0, 8, 13] },
  { id: "A3", type: "normale",    rect: [30, 0, 8, 13] },
  { id: "A4", type: "normale",    rect: [38, 0, 8, 13] },
  { id: "B1", type: "normale",    rect: [14, 27, 8, 13] },
  { id: "B2", type: "normale",    rect: [22, 27, 8, 13] },
  { id: "B3", type: "normale",    rect: [30, 27, 8, 13] },
  { id: "B4", type: "electrique", rect: [38, 27, 8, 13] },
];

db.parkings.replaceOne(
  { _id: "P1" },
  {
    _id: "P1",
    nom: "Maquette SUP'COM",
    resolution: RESOLUTION,
    dimensions_cm: DIMENSIONS_CM,
    zones_interdites: [
      { id: "allee1", poly: polyDepuisRect([50, 13, 10, 14]), rect_cm: [50, 13, 10, 14] },
    ],
    maj_le: new Date(),
  },
  { upsert: true }
);

for (const p of places) {
  db.places.replaceOne(
    { _id: p.id },
    {
      _id: p.id,
      parking: "P1",
      type: p.type,
      duree_max_min: p.type === "electrique" ? 60 : 120,
      poly: polyDepuisRect(p.rect),
      rect_cm: p.rect,
      noeud: p.id,
      etat: "libre",
      depuis: null,
      verrou: null,
    },
    { upsert: true }
  );
}

db.places.createIndex({ parking: 1 }, { name: "parking" });

print(`Seed terminé : ${db.parkings.countDocuments()} parking, ${db.places.countDocuments({ parking: "P1" })} places.`);