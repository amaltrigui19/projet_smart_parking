// Relit le parking P1 et ses places : démo « polygones enregistrés et relus par un script ».
const parking = db.parkings.findOne({ _id: "P1" });
if (!parking) {
  print("Aucun parking P1 : lancer d'abord seed.js.");
  quit(1);
}
print(`${parking._id} - ${parking.name} - caméra ${parking.resolution.join("x")} px`);
db.spots.find({ parking: "P1" }).sort({ _id: 1 }).forEach((p) =>
  print(`${p._id.padEnd(3)} ${p.type.padEnd(10)} ${p.state.padEnd(8)} polygon=${JSON.stringify(p.polygon)}`)
);
parking.forbidden_zones.forEach((z) => print(`zone ${z.id.padEnd(7)} polygon=${JSON.stringify(z.polygon)}`));