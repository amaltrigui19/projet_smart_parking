// Seed : parking P1 de la maquette (contrat 4.1 et 4.3) et ses 8 places.
// Relançable sans risque : chaque document est remplacé (upsert), rien n'est dupliqué.

const RESOLUTION = [640, 480];      // image de la caméra, en pixels (contrat 5.2)
const DIMENSIONS_CM = [45, 30];     // maquette, en cm (contrat 4.3)
const SCALE_X = RESOLUTION[0] / DIMENSIONS_CM[0];   // pixels par cm
const SCALE_Y = RESOLUTION[1] / DIMENSIONS_CM[1];

// Polygone PROVISOIRE : le rectangle du plan converti en pixels, comme si la caméra était
// parfaitement au-dessus de la maquette. Il sera remplacé par la vraie calibration.
function polygonFromRect([x, y, l, h]) {
  const px = (v) => Math.round(v * SCALE_X);
  const py = (v) => Math.round(v * SCALE_Y);
  return [[px(x), py(y)], [px(x + l), py(y)], [px(x + l), py(y + h)], [px(x), py(y + h)]];
}

const spots = [
  { id: "A1", type: "accessible", rect: [10, 0, 6, 10] },
  { id: "A2", type: "standard",   rect: [16, 0, 6, 10] },
  { id: "A3", type: "standard",   rect: [22, 0, 6, 10] },
  { id: "A4", type: "standard",   rect: [28, 0, 6, 10] },
  { id: "B1", type: "standard",   rect: [10, 20, 6, 10] },
  { id: "B2", type: "standard",   rect: [16, 20, 6, 10] },
  { id: "B3", type: "standard",   rect: [22, 20, 6, 10] },
  { id: "B4", type: "electric",   rect: [28, 20, 6, 10] },
];

db.parkings.replaceOne(
  { _id: "P1" },
  {
    _id: "P1",
    name: "Maquette SUP'COM",
    resolution: RESOLUTION,
    dimensions_cm: DIMENSIONS_CM,
    forbidden_zones: [
      { id: "allee1", polygon: polygonFromRect([37, 10, 8, 10]), rect_cm: [37, 10, 8, 10] },
    ],
    updated_at: new Date(),
  },
  { upsert: true }
);

for (const p of spots) {
  db.spots.replaceOne(
    { _id: p.id },
    {
      _id: p.id,
      parking: "P1",
      type: p.type,
      max_duration_min: p.type === "electric" ? 60 : 120,
      polygon: polygonFromRect(p.rect),
      rect_cm: p.rect,
      node: p.id,
      state: "free",
      since: null,
      lock: null,
    },
    { upsert: true }
  );
}

db.spots.createIndex({ parking: 1 }, { name: "parking" });

print(`Seed terminé : ${db.parkings.countDocuments()} parking, ${db.spots.countDocuments({ parking: "P1" })} places.`);