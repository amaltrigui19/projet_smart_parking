"use strict";

// Validation de places.json AVANT l'envoi au serveur, avec les mêmes règles que le serveur
// (docs/contrats.md §2.10, erreur 400 config_invalide) et que frontend/outils/valider_places.py :
//   - parking et résolution présents ;
//   - identifiant valide et unique (places et zones confondues) ;
//   - type connu, durée maximale entière ≥ 1 ;
//   - polygone d'au moins 3 points entiers, dans l'image, d'aire non nulle ;
//   - chaque place existe dans le plan du parking (contrat §4).
// Utilisable dans le navigateur (fonction globale validerPlaces) et dans Node (tests).

(function (racine) {
  const TYPES = ["normale", "pmr", "electrique"];
  const ID_VALIDE = /^[A-Za-z0-9_-]+$/;
  const estEntier = (v) => Number.isInteger(v);

  // Aire d'un polygone (formule du lacet).
  function aire(poly) {
    let s = 0;
    poly.forEach(([x1, y1], i) => {
      const [x2, y2] = poly[(i + 1) % poly.length];
      s += x1 * y2 - x2 * y1;
    });
    return Math.abs(s) / 2;
  }

  function verifierPoly(nom, poly, largeur, hauteur, erreurs) {
    if (!Array.isArray(poly) || poly.length < 3) {
      erreurs.push(`${nom} : le polygone doit avoir au moins 3 points`);
      return;
    }
    for (let i = 0; i < poly.length; i++) {
      const p = poly[i];
      if (!(Array.isArray(p) && p.length === 2 && p.every(estEntier))) {
        erreurs.push(`${nom} : le point ${i + 1} doit être [x, y] en pixels entiers`);
        return;
      }
      const [x, y] = p;
      if (!(x >= 0 && x < largeur && y >= 0 && y < hauteur)) {
        erreurs.push(`${nom} : le point ${i + 1} (${x}, ${y}) sort de l'image ${largeur} × ${hauteur}`);
      }
    }
    if (aire(poly) === 0) erreurs.push(`${nom} : le polygone est plat (aire nulle)`);
  }

  // donnees : le contenu de places.json ; planIds : identifiants des places du plan (ex. A1…B4).
  // Renvoie la liste des erreurs, vide si le fichier est valide.
  function validerPlaces(donnees, planIds) {
    const erreurs = [];
    if (!donnees || typeof donnees !== "object") return ["le fichier doit être un objet JSON"];

    if (typeof donnees.parking !== "string" || !donnees.parking) {
      erreurs.push("'parking' doit être un texte non vide, par exemple \"P1\"");
    }
    const res = donnees.resolution;
    let largeur = Infinity;
    let hauteur = Infinity;
    if (Array.isArray(res) && res.length === 2 && res.every((v) => estEntier(v) && v > 0)) {
      [largeur, hauteur] = res;
    } else {
      erreurs.push("'resolution' doit être [largeur, hauteur] en pixels");
    }

    const vus = new Set();
    const verifierId = (nom, element) => {
      const id = element.id;
      if (typeof id !== "string" || !ID_VALIDE.test(id)) {
        erreurs.push(`${nom} : identifiant manquant ou invalide (lettres sans accent, chiffres, - et _)`);
        return nom;
      }
      if (vus.has(id)) erreurs.push(`${id} : identifiant en double`);
      vus.add(id);
      return id;
    };

    const plan = planIds ? new Set(planIds) : null;
    const places = Array.isArray(donnees.places) ? donnees.places : (erreurs.push("'places' doit être une liste"), []);
    places.forEach((p, i) => {
      if (!p || typeof p !== "object") { erreurs.push(`places[${i}] doit être un objet`); return; }
      const nom = verifierId(`places[${i}]`, p);
      if (!TYPES.includes(p.type)) erreurs.push(`${nom} : type « ${p.type} » inconnu (normale, pmr ou electrique)`);
      if (!(estEntier(p.duree_max_min) && p.duree_max_min >= 1)) {
        erreurs.push(`${nom} : la durée maximale doit être un entier ≥ 1 minute`);
      }
      if (plan && typeof p.id === "string" && !plan.has(p.id)) {
        erreurs.push(`${nom} : cette place n'existe pas dans le plan du parking (${[...plan].join(", ")})`);
      }
      verifierPoly(nom, p.poly, largeur, hauteur, erreurs);
    });

    const zones = Array.isArray(donnees.zones_interdites)
      ? donnees.zones_interdites
      : (erreurs.push("'zones_interdites' doit être une liste"), []);
    zones.forEach((z, i) => {
      if (!z || typeof z !== "object") { erreurs.push(`zones_interdites[${i}] doit être un objet`); return; }
      verifierPoly(verifierId(`zones_interdites[${i}]`, z), z.poly, largeur, hauteur, erreurs);
    });

    return erreurs;
  }

  racine.validerPlaces = validerPlaces;
  if (typeof module !== "undefined" && module.exports) module.exports = { validerPlaces, aire };
})(typeof window !== "undefined" ? window : globalThis);
