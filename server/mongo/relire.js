// Relit le parking P1 et ses places : démo « polygones enregistrés et relus par un script ».
const parking = db.parkings.findOne({ _id: "P1" });
if (!parking) {
  print("Aucun parking P1 : lancer d'abord seed.js.");
  quit(1);
}
print(`${parking._id} - ${parking.nom} - caméra ${parking.resolution.join("x")} px`);
db.places.find({ parking: "P1" }).sort({ _id: 1 }).forEach((p) =>
  print(`${p._id.padEnd(3)} ${p.type.padEnd(10)} ${p.etat.padEnd(7)} poly=${JSON.stringify(p.poly)}`)
);
parking.zones_interdites.forEach((z) => print(`zone ${z.id.padEnd(7)} poly=${JSON.stringify(z.poly)}`));