"use strict";

// Validation of places.json BEFORE sending it to the server, with the same rules as the server
// (CalibrationValidator, docs/contrats.md §2.10, error 400 invalid_config) and frontend/tools/validate_spots.py:
//   - parking and resolution present;
//   - valid and unique id (spots and zones together);
//   - known type, integer max duration ≥ 1;
//   - polygon of at least 3 integer points, inside the image, with a non-zero area;
//   - each spot exists in the parking plan (contract §4).
// Works in the browser (global function validateSpots) and in Node (tests).

(function (root) {
  const TYPES = ["standard", "accessible", "electric"];
  const VALID_ID = /^[A-Za-z0-9_-]+$/;
  const isInteger = (v) => Number.isInteger(v);

  // Area of a polygon (shoelace formula).
  function area(polygon) {
    let s = 0;
    polygon.forEach(([x1, y1], i) => {
      const [x2, y2] = polygon[(i + 1) % polygon.length];
      s += x1 * y2 - x2 * y1;
    });
    return Math.abs(s) / 2;
  }

  function checkPolygon(label, polygon, width, height, errors) {
    if (!Array.isArray(polygon) || polygon.length < 3) {
      errors.push(`${label} : le polygone doit avoir au moins 3 points`);
      return;
    }
    for (let i = 0; i < polygon.length; i++) {
      const p = polygon[i];
      if (!(Array.isArray(p) && p.length === 2 && p.every(isInteger))) {
        errors.push(`${label} : le point ${i + 1} doit être [x, y] en pixels entiers`);
        return;
      }
      const [x, y] = p;
      if (!(x >= 0 && x < width && y >= 0 && y < height)) {
        errors.push(`${label} : le point ${i + 1} (${x}, ${y}) sort de l'image ${width} × ${height}`);
      }
    }
    if (area(polygon) === 0) errors.push(`${label} : le polygone est plat (aire nulle)`);
  }

  // data: content of places.json; planIds: ids of the spots of the plan (e.g. A1…B4).
  // Returns the list of errors, empty if the file is valid.
  function validateSpots(data, planIds) {
    const errors = [];
    if (!data || typeof data !== "object") return ["le fichier doit être un objet JSON"];

    if (typeof data.parking !== "string" || !data.parking) {
      errors.push("'parking' doit être un texte non vide, par exemple \"P1\"");
    }
    const res = data.resolution;
    let width = Infinity;
    let height = Infinity;
    if (Array.isArray(res) && res.length === 2 && res.every((v) => isInteger(v) && v > 0)) {
      [width, height] = res;
    } else {
      errors.push("'resolution' doit être [largeur, hauteur] en pixels");
    }

    const seen = new Set();
    const checkId = (label, element) => {
      const id = element.id;
      if (typeof id !== "string" || !VALID_ID.test(id)) {
        errors.push(`${label} : identifiant manquant ou invalide (lettres sans accent, chiffres, - et _)`);
        return label;
      }
      if (seen.has(id)) errors.push(`${id} : identifiant en double`);
      seen.add(id);
      return id;
    };

    const plan = planIds ? new Set(planIds) : null;
    const spots = Array.isArray(data.spots) ? data.spots : (errors.push("'spots' doit être une liste"), []);
    spots.forEach((s, i) => {
      if (!s || typeof s !== "object") { errors.push(`spots[${i}] doit être un objet`); return; }
      const label = checkId(`spots[${i}]`, s);
      if (!TYPES.includes(s.type)) errors.push(`${label} : type « ${s.type} » inconnu (${TYPES.join(", ")})`);
      if (!(isInteger(s.max_duration_min) && s.max_duration_min >= 1)) {
        errors.push(`${label} : la durée maximale doit être un entier ≥ 1 minute`);
      }
      if (plan && typeof s.id === "string" && !plan.has(s.id)) {
        errors.push(`${label} : cette place n'existe pas dans le plan du parking (${[...plan].join(", ")})`);
      }
      checkPolygon(label, s.polygon, width, height, errors);
    });

    const zones = Array.isArray(data.forbidden_zones)
      ? data.forbidden_zones
      : (errors.push("'forbidden_zones' doit être une liste"), []);
    zones.forEach((z, i) => {
      if (!z || typeof z !== "object") { errors.push(`forbidden_zones[${i}] doit être un objet`); return; }
      checkPolygon(checkId(`forbidden_zones[${i}]`, z), z.polygon, width, height, errors);
    });

    return errors;
  }

  root.validateSpots = validateSpots;
  if (typeof module !== "undefined" && module.exports) module.exports = { validateSpots, area };
})(typeof window !== "undefined" ? window : globalThis);
