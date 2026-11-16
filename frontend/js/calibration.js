"use strict";

// Calibration des places : on dessine les polygones sur la photo de référence
// et on exporte places.json au format du contrat (docs/contrats.md §5.2).

const PARKING = "P1";
const RESOLUTION_PI = [640, 480];
const DUREE_PAR_DEFAUT = { normale: 120, pmr: 120, electrique: 60 };
// Plan validé (docs/contrats.md §4) : sert seulement à proposer le nom et le type suivants.
const PLAN = [
  ["A1", "pmr"], ["A2", "normale"], ["A3", "normale"], ["A4", "normale"],
  ["B1", "normale"], ["B2", "normale"], ["B3", "normale"], ["B4", "electrique"],
];
const COULEURS = { normale: "#3fb3b3", pmr: "#5b8def", electrique: "#e3b341", zone: "#e5534b" };
const PLAN_IDS = PLAN.map(([id]) => id);

// API (docs/contrats.md §2.10) : même domaine que la PWA, donc une adresse relative.
const API_POLYGONES = "/api/config/polygones";
// Jeton JWT : écrit par la page de connexion (sprint 3, Eya), lu ici. Contrat §1.2 : sessionStorage.
// C'est le SEUL endroit à changer si la page de connexion range le jeton ailleurs.
const STOCKAGE_JWT = () => window.sessionStorage;
const CLE_JWT = "jwt";

// resolution : [largeur, hauteur] de la photo, ou du fichier chargé depuis le serveur.
// enEdition : élément retiré de la liste pendant qu'on le modifie (remis en place si on annule).
const etat = {
  image: null, resolution: null, points: [], places: [], zones: [],
  formulaireOuvert: false, enEdition: null, nonEnregistre: false, envoiEnCours: false,
};

const el = (id) => document.getElementById(id);
const canvas = el("canvas");
const ctx = canvas.getContext("2d");
const champFichier = el("fichier-photo");
const vide = el("zone-vide");
const aide = el("aide");
const infoResolution = el("info-resolution");
const btnAnnulerPoint = el("btn-annuler-point");
const btnFermer = el("btn-fermer");
const btnEffacer = el("btn-effacer");
const formulaire = el("formulaire");
const champId = el("champ-id");
const champType = el("champ-type");
const champDuree = el("champ-duree");
const blocPlace = el("bloc-place");
const messageFormulaire = el("message-formulaire");
const liste = el("liste");
const compteur = el("compteur");
const btnExporter = el("btn-exporter");
const btnEnregistrerServeur = el("btn-enregistrer-serveur");
const messageServeur = el("message-serveur");

const borner = (v, min, max) => Math.min(max, Math.max(min, v));
const trier = (t) => [...t].sort((a, b) => a.id.localeCompare(b.id, "fr", { numeric: true }));
const idsUtilises = () => new Set([...etat.places, ...etat.zones].map((e) => e.id));
const centre = (poly) => [
  poly.reduce((s, p) => s + p[0], 0) / poly.length,
  poly.reduce((s, p) => s + p[1], 0) / poly.length,
];

// ---------- Photo ----------
champFichier.addEventListener("change", () => {
  const fichier = champFichier.files[0];
  if (!fichier) return;
  const url = URL.createObjectURL(fichier);
  const img = new Image();
  img.onload = () => {
    annulerEdition(); // un élément en cours de modification reprend sa place avant toute décision
    // La résolution connue vient d'une photo précédente ou du fichier chargé depuis le serveur.
    const res = etat.resolution;
    const memeTaille = res !== null && img.naturalWidth === res[0] && img.naturalHeight === res[1];
    const aDesElements = etat.places.length + etat.zones.length > 0;
    if (aDesElements && !memeTaille &&
        !confirm(`Cette photo (${img.naturalWidth} × ${img.naturalHeight}) n'a pas la même résolution que les ` +
                 `polygones (${res[0]} × ${res[1]}) : ils seront effacés. Continuer ?`)) {
      URL.revokeObjectURL(url);
      champFichier.value = "";
      return;
    }
    if (!memeTaille && aDesElements) { etat.places = []; etat.zones = []; etat.nonEnregistre = true; }
    if (etat.image) URL.revokeObjectURL(etat.image.src);
    etat.image = img;
    etat.resolution = [img.naturalWidth, img.naturalHeight];
    etat.points = [];
    canvas.width = img.naturalWidth;
    canvas.height = img.naturalHeight;
    canvas.hidden = false;
    vide.hidden = true;
    afficherResolution();
    fermerFormulaire();
    toutMettreAJour();
  };
  img.onerror = () => {
    URL.revokeObjectURL(url);
    aide.textContent = "Ce fichier n'est pas une image lisible. Choisis une photo JPG ou PNG.";
  };
  img.src = url;
});

function afficherResolution() {
  const [l, h] = [canvas.width, canvas.height];
  const ok = l === RESOLUTION_PI[0] && h === RESOLUTION_PI[1];
  infoResolution.textContent = ok
    ? `Résolution de la photo : ${l} × ${h} px.`
    : `Résolution de la photo : ${l} × ${h} px. Le Pi analyse en ${RESOLUTION_PI[0]} × ${RESOLUTION_PI[1]} : ` +
      "prends la photo de référence avec le Pi à cette résolution, sinon les polygones seront décalés.";
  infoResolution.className = ok ? "info" : "info avertissement";
}

// ---------- Tracé ----------
// La photo peut être affichée plus petite que sa vraie taille :
// on convertit la position du clic en pixels de la vraie photo.
function positionDansImage(evt) {
  const r = canvas.getBoundingClientRect();
  const x = Math.round(((evt.clientX - r.left) / r.width) * canvas.width);
  const y = Math.round(((evt.clientY - r.top) / r.height) * canvas.height);
  return [borner(x, 0, canvas.width - 1), borner(y, 0, canvas.height - 1)];
}

canvas.addEventListener("pointerdown", (evt) => {
  if (!etat.image || etat.formulaireOuvert) return;
  evt.preventDefault();
  etat.points.push(positionDansImage(evt));
  toutMettreAJour();
});

// Si on modifiait un élément existant, il reprend sa place tel qu'il était.
function annulerEdition() {
  if (!etat.enEdition) return;
  const { cat, element } = etat.enEdition;
  (cat === "zone" ? etat.zones : etat.places).push(element);
  etat.enEdition = null;
  etat.points = [];
}

function annulerPoint() { etat.points.pop(); toutMettreAJour(); }
function effacerTrace() { etat.points = []; annulerEdition(); toutMettreAJour(); }
function fermerPolygone() {
  if (etat.points.length < 3) return;
  // Si on retouche un élément existant, le formulaire reprend ses valeurs.
  ouvrirFormulaire(etat.enEdition ? { cat: etat.enEdition.cat, ...etat.enEdition.element } : null);
}

btnAnnulerPoint.addEventListener("click", annulerPoint);
btnEffacer.addEventListener("click", effacerTrace);
btnFermer.addEventListener("click", fermerPolygone);

document.addEventListener("keydown", (evt) => {
  if (!etat.image || etat.formulaireOuvert) return;
  if (evt.target.closest("input, select, textarea, button, label")) return;
  if (evt.key === "Enter") { evt.preventDefault(); fermerPolygone(); }
  else if (evt.key === "Backspace") { evt.preventDefault(); annulerPoint(); }
  else if (evt.key === "Escape") { effacerTrace(); }
});

// ---------- Formulaire ----------
const categorie = () => formulaire.elements.categorie.value; // "place" ou "zone"

function proposerValeurs() {
  const utilises = idsUtilises();
  const zone = categorie() === "zone";
  blocPlace.hidden = zone;
  if (zone) {
    let n = 1;
    while (utilises.has(`allee${n}`)) n++;
    champId.value = `allee${n}`;
    return;
  }
  const suivante = PLAN.find(([id]) => !utilises.has(id));
  if (!suivante) {
    // Toutes les places du plan sont dessinées : le serveur refuse une place hors du plan (contrat §2.10).
    champId.value = "";
    messageFormulaire.textContent = `Toutes les places du plan (${PLAN_IDS.join(", ")}) sont déjà dessinées. ` +
      "Supprime ou modifie une place existante, ou choisis « zone interdite ».";
    return;
  }
  const [id, type] = suivante;
  champId.value = id;
  champType.value = type;
  champDuree.value = DUREE_PAR_DEFAUT[type];
}

// valeurs : élément existant à modifier ({ cat, id, type, duree_max_min }), sinon on propose les suivantes.
function ouvrirFormulaire(valeurs = null) {
  etat.formulaireOuvert = true;
  formulaire.hidden = false;
  messageFormulaire.textContent = "";
  if (valeurs) {
    formulaire.elements.categorie.value = valeurs.cat;
    blocPlace.hidden = valeurs.cat === "zone";
    champId.value = valeurs.id;
    if (valeurs.cat === "place") {
      champType.value = valeurs.type;
      champDuree.value = valeurs.duree_max_min;
    }
  } else {
    proposerValeurs();
  }
  // Sans photo, on ne peut pas retoucher les points : seulement le nom, le type et la durée.
  el("btn-abandonner").disabled = !etat.image;
  toutMettreAJour();
  champId.focus();
  champId.select();
}

function fermerFormulaire() {
  etat.formulaireOuvert = false;
  formulaire.hidden = true;
}

formulaire.addEventListener("change", (evt) => {
  if (evt.target.name === "categorie") proposerValeurs();
  if (evt.target === champType) champDuree.value = DUREE_PAR_DEFAUT[champType.value];
});

function verifierId(id) {
  if (!id) return "Donne un nom à cet élément, par exemple A1 ou allee1.";
  if (!/^[A-Za-z0-9_-]+$/.test(id)) return "Le nom ne peut contenir que des lettres sans accent, des chiffres, - et _.";
  if (idsUtilises().has(id)) return `Le nom ${id} est déjà utilisé.`;
  return "";
}

formulaire.addEventListener("submit", (evt) => {
  evt.preventDefault();
  const id = champId.value.trim();
  const erreur = verifierId(id) || (categorie() === "place" && !PLAN_IDS.includes(id)
    ? `La place ${id} n'existe pas dans le plan du parking (${PLAN_IDS.join(", ")}).` : "");
  if (erreur) { messageFormulaire.textContent = erreur; champId.focus(); return; }
  const poly = etat.points.map(([x, y]) => [x, y]);
  if (categorie() === "zone") {
    etat.zones.push({ id, poly });
  } else {
    const duree = Number(champDuree.value);
    if (!Number.isInteger(duree) || duree < 1) {
      messageFormulaire.textContent = "La durée maximale doit être un nombre entier de minutes, au moins 1.";
      champDuree.focus();
      return;
    }
    etat.places.push({ id, type: champType.value, duree_max_min: duree, poly });
  }
  etat.points = [];
  etat.enEdition = null;
  etat.nonEnregistre = true;
  fermerFormulaire();
  toutMettreAJour();
});

// « Modifier le tracé » : on garde les points pour pouvoir les corriger.
el("btn-abandonner").addEventListener("click", () => { fermerFormulaire(); toutMettreAJour(); });

// « Annuler » : on abandonne ce polygone (un élément existant reprend sa place, inchangé).
el("btn-annuler-formulaire").addEventListener("click", () => {
  etat.points = [];
  annulerEdition();
  fermerFormulaire();
  toutMettreAJour();
});

// ---------- Liste ----------
function supprimer(cat, id) {
  if (cat === "zone") etat.zones = etat.zones.filter((z) => z.id !== id);
  else etat.places = etat.places.filter((p) => p.id !== id);
  etat.nonEnregistre = true;
  toutMettreAJour();
}

// « Modifier » : l'élément quitte la liste, ses points deviennent le tracé en cours et le formulaire
// s'ouvre avec ses valeurs. « Annuler » le remet tel quel.
function modifier(cat, id) {
  if (etat.formulaireOuvert) return;
  annulerEdition();
  const tableau = cat === "zone" ? etat.zones : etat.places;
  const element = tableau.find((e) => e.id === id);
  if (!element) return;
  tableau.splice(tableau.indexOf(element), 1);
  etat.enEdition = { cat, element };
  etat.points = element.poly.map(([x, y]) => [x, y]);
  ouvrirFormulaire({ cat, ...element });
}

function afficherListe() {
  liste.replaceChildren();
  const elements = [
    ...trier(etat.places).map((p) => ({ ...p, cat: "place" })),
    ...trier(etat.zones).map((z) => ({ ...z, cat: "zone" })),
  ];
  for (const e of elements) {
    const li = document.createElement("li");
    const pastille = document.createElement("span");
    pastille.className = "pastille-couleur";
    pastille.style.background = e.cat === "zone" ? COULEURS.zone : COULEURS[e.type];
    const texte = document.createElement("span");
    texte.className = "texte-element";
    texte.textContent = e.cat === "zone"
      ? `${e.id}, zone interdite`
      : `${e.id}, ${e.type}, ${e.duree_max_min} min`;
    const btnModifier = document.createElement("button");
    btnModifier.type = "button";
    btnModifier.className = "bouton-secondaire petit";
    btnModifier.textContent = "Modifier";
    btnModifier.setAttribute("aria-label", `Modifier ${e.id}`);
    btnModifier.disabled = etat.formulaireOuvert;
    btnModifier.addEventListener("click", () => modifier(e.cat, e.id));
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "bouton-secondaire petit";
    btn.textContent = "Supprimer";
    btn.setAttribute("aria-label", `Supprimer ${e.id}`);
    btn.addEventListener("click", () => supprimer(e.cat, e.id));
    li.append(pastille, texte, btnModifier, btn);
    liste.append(li);
  }
  compteur.textContent = `${etat.places.length} place(s), ${etat.zones.length} zone(s) interdite(s).` +
    (etat.nonEnregistre ? " Modifications non enregistrées sur le serveur." : "");
}

// ---------- Dessin ----------
function dessinerPolygone(poly, couleur, nom, trait, police) {
  ctx.save();
  ctx.beginPath();
  poly.forEach(([x, y], i) => (i ? ctx.lineTo(x, y) : ctx.moveTo(x, y)));
  ctx.closePath();
  ctx.globalAlpha = 0.28;
  ctx.fillStyle = couleur;
  ctx.fill();
  ctx.globalAlpha = 1;
  ctx.lineWidth = trait;
  ctx.strokeStyle = couleur;
  ctx.stroke();
  const [cx, cy] = centre(poly);
  ctx.font = `600 ${police}px system-ui, sans-serif`;
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  ctx.lineWidth = Math.max(3, police / 5);
  ctx.strokeStyle = "rgba(0, 0, 0, .75)";
  ctx.strokeText(nom, cx, cy);
  ctx.fillStyle = "#ffffff";
  ctx.fillText(nom, cx, cy);
  ctx.restore();
}

function dessiner() {
  if (!etat.image) return;
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  ctx.drawImage(etat.image, 0, 0);
  const trait = Math.max(2, canvas.width / 320);
  const police = Math.max(12, Math.round(canvas.width / 40));
  for (const z of etat.zones) dessinerPolygone(z.poly, COULEURS.zone, z.id, trait, police);
  for (const p of etat.places) dessinerPolygone(p.poly, COULEURS[p.type], p.id, trait, police);

  if (!etat.points.length) return;
  ctx.save();
  ctx.strokeStyle = "#ffffff";
  ctx.lineWidth = trait;
  ctx.setLineDash([trait * 3, trait * 2]);
  ctx.beginPath();
  etat.points.forEach(([x, y], i) => (i ? ctx.lineTo(x, y) : ctx.moveTo(x, y)));
  if (etat.points.length >= 3) ctx.closePath();
  ctx.stroke();
  ctx.setLineDash([]);
  for (const [x, y] of etat.points) {
    ctx.beginPath();
    ctx.arc(x, y, trait * 2, 0, Math.PI * 2);
    ctx.fillStyle = "#ffffff";
    ctx.fill();
    ctx.lineWidth = 1;
    ctx.strokeStyle = "#000000";
    ctx.stroke();
  }
  ctx.restore();
}

// ---------- Fichier places.json (contrat §5.2) ----------
function construireFichier() {
  return {
    parking: PARKING,
    resolution: [...etat.resolution],
    places: trier(etat.places).map(({ id, type, duree_max_min, poly }) => ({ id, type, duree_max_min, poly })),
    zones_interdites: trier(etat.zones).map(({ id, poly }) => ({ id, poly })),
  };
}

// ---------- Export ----------
btnExporter.addEventListener("click", () => {
  const donnees = construireFichier();
  const blob = new Blob([JSON.stringify(donnees, null, 2) + "\n"], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = "places.json";
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
});

// ---------- Serveur : GET et PUT /api/config/polygones (contrat §2.10) ----------
function lireJeton() {
  try { return STOCKAGE_JWT().getItem(CLE_JWT); } catch { return null; }
}
function effacerJeton() {
  try { STOCKAGE_JWT().removeItem(CLE_JWT); } catch { /* stockage indisponible : rien à effacer */ }
}

// Affiche un message dans le panneau. genre : "succes", "erreur" ou "info". details : liste de lignes.
function afficherMessage(genre, texte, details = []) {
  messageServeur.className = `message-serveur ${genre}`;
  messageServeur.setAttribute("role", genre === "erreur" ? "alert" : "status");
  messageServeur.replaceChildren();
  const p = document.createElement("p");
  p.textContent = texte;
  messageServeur.append(p);
  if (details.length) {
    const ul = document.createElement("ul");
    for (const d of details) {
      const li = document.createElement("li");
      li.textContent = d;
      ul.append(li);
    }
    messageServeur.append(ul);
  }
}

// Appel à l'API avec le JWT. Renvoie { statut, corps } ; statut 0 = serveur injoignable.
async function appelerApi(methode, corps) {
  const entetes = { Accept: "application/json" };
  const jeton = lireJeton();
  if (jeton) entetes.Authorization = `Bearer ${jeton}`;
  if (corps !== undefined) entetes["Content-Type"] = "application/json";
  let reponse;
  try {
    reponse = await fetch(API_POLYGONES, {
      method: methode,
      headers: entetes,
      body: corps === undefined ? undefined : JSON.stringify(corps),
      cache: "no-store",
    });
  } catch {
    return { statut: 0, corps: null };
  }
  let json = null;
  try { json = await reponse.json(); } catch { /* réponse sans JSON */ }
  return { statut: reponse.status, corps: json };
}

// Messages communs à GET et PUT pour 401, 403, serveur injoignable et autres erreurs.
function messageErreurCommune({ statut, corps }) {
  if (statut === 0) {
    return location.protocol === "file:"
      ? "Page ouverte sans serveur (double-clic) : l'enregistrement est impossible. Lance la fausse API " +
        "(python frontend/outils/fausse_api.py) puis ouvre http://localhost:8000/calibration.html."
      : "Serveur injoignable : vérifie qu'il est lancé (ou la fausse API), puis réessaie.";
  }
  if (statut === 401) {
    effacerJeton(); // contrat §1.2 : sur 401, la PWA efface le jeton
    return "Non connecté, ou session expirée (401). Connecte-toi avec un compte exploitant, puis réessaie.";
  }
  if (statut === 403) return "Accès refusé (403) : cette page est réservée à l'exploitant.";
  const detail = corps && corps.message ? ` : ${corps.message}` : "";
  return `Erreur du serveur (${statut}${corps && corps.erreur ? `, ${corps.erreur}` : ""})${detail}`;
}

// Au chargement de la page : on récupère les polygones déjà enregistrés, s'il y en a.
async function chargerDepuisServeur() {
  if (location.protocol === "file:") {
    afficherMessage("info", "Page ouverte sans serveur : tu peux dessiner et exporter places.json, " +
      "mais pas enregistrer sur le serveur. Voir frontend/README.md (fausse API).");
    return;
  }
  afficherMessage("info", "Recherche des polygones déjà enregistrés…");
  const r = await appelerApi("GET");
  if (r.statut === 404) {
    afficherMessage("info", "Aucun fichier enregistré sur le serveur : on part d'un plan vide.");
    return;
  }
  if (r.statut !== 200) { afficherMessage("erreur", messageErreurCommune(r)); return; }
  const d = r.corps;
  if (!d || !Array.isArray(d.places) || !Array.isArray(d.zones_interdites) || !Array.isArray(d.resolution)) {
    afficherMessage("erreur", "Le serveur a renvoyé un fichier de polygones illisible : on part d'un plan vide.");
    return;
  }
  if (etat.places.length + etat.zones.length > 0) {
    afficherMessage("info", "Des polygones existent sur le serveur, mais tu as déjà commencé à dessiner : " +
      "recharge la page pour les afficher.");
    return;
  }
  etat.places = d.places.map(({ id, type, duree_max_min, poly }) => ({ id, type, duree_max_min, poly }));
  etat.zones = d.zones_interdites.map(({ id, poly }) => ({ id, poly }));
  etat.resolution = [d.resolution[0], d.resolution[1]];
  etat.nonEnregistre = false;
  afficherMessage("succes", `${etat.places.length} place(s) et ${etat.zones.length} zone(s) chargées depuis le ` +
    `serveur. Choisis la photo de référence (${etat.resolution[0]} × ${etat.resolution[1]}) pour les voir et les modifier.`);
  toutMettreAJour();
}

// « Enregistrer sur le serveur » : validation locale (mêmes règles que le serveur), puis PUT du fichier complet.
btnEnregistrerServeur.addEventListener("click", async () => {
  const donnees = construireFichier();
  const erreurs = window.validerPlaces(donnees, PLAN_IDS);
  if (erreurs.length) {
    afficherMessage("erreur", `Le fichier n'est pas valide (${erreurs.length} problème(s)), rien n'a été envoyé :`, erreurs);
    return;
  }
  etat.envoiEnCours = true;
  majBoutons();
  afficherMessage("info", "Envoi au serveur…");
  const r = await appelerApi("PUT", donnees);
  etat.envoiEnCours = false;
  if (r.statut === 200) {
    etat.nonEnregistre = false;
    const heure = new Date().toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" });
    afficherMessage("succes", `Enregistré sur le serveur à ${heure} : ${donnees.places.length} place(s), ` +
      `${donnees.zones_interdites.length} zone(s) interdite(s).`);
  } else if (r.statut === 400 && r.corps && r.corps.erreur === "config_invalide") {
    afficherMessage("erreur", "Le serveur a refusé le fichier (config_invalide) :",
      String(r.corps.message || "").split(" ; ").filter(Boolean));
  } else {
    afficherMessage("erreur", messageErreurCommune(r));
  }
  toutMettreAJour();
});

// ---------- Mise à jour générale ----------
function majAide() {
  const n = etat.points.length;
  if (!etat.image && etat.places.length + etat.zones.length > 0) {
    aide.textContent = "Choisis la photo de référence pour voir et modifier les polygones chargés.";
  } else if (!etat.image) aide.textContent = "Commence par choisir la photo de référence de la maquette.";
  else if (etat.formulaireOuvert) aide.textContent = "Donne un nom et un type à ce polygone, puis enregistre-le.";
  else if (n === 0) aide.textContent = "Clique sur les coins d'une place, dans l'ordre, en faisant le tour.";
  else if (n < 3) aide.textContent = `${n} point(s) placé(s). Il en faut au moins 3.`;
  else aide.textContent = `${n} points placés. Clique sur « Fermer le polygone » (ou appuie sur Entrée) quand le tour est fait.`;
}

function majBoutons() {
  const n = etat.points.length;
  const ouvert = etat.formulaireOuvert;
  btnAnnulerPoint.disabled = n === 0 || ouvert;
  btnEffacer.disabled = n === 0 || ouvert;
  btnFermer.disabled = n < 3 || ouvert;
  const rien = etat.resolution === null || ouvert || etat.places.length + etat.zones.length === 0;
  btnExporter.disabled = rien;
  btnEnregistrerServeur.disabled = rien || etat.envoiEnCours === true;
}

function toutMettreAJour() {
  dessiner();
  afficherListe();
  majBoutons();
  majAide();
}

toutMettreAJour();
chargerDepuisServeur();