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

const etat = { image: null, points: [], places: [], zones: [], formulaireOuvert: false };

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
    const memeTaille = etat.image !== null &&
      img.naturalWidth === canvas.width && img.naturalHeight === canvas.height;
    const aDesElements = etat.places.length + etat.zones.length > 0;
    if (aDesElements && !memeTaille &&
        !confirm("Cette photo n'a pas la même résolution : les polygones déjà dessinés seront effacés. Continuer ?")) {
      URL.revokeObjectURL(url);
      champFichier.value = "";
      return;
    }
    if (!memeTaille) { etat.places = []; etat.zones = []; }
    if (etat.image) URL.revokeObjectURL(etat.image.src);
    etat.image = img;
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

function annulerPoint() { etat.points.pop(); toutMettreAJour(); }
function effacerTrace() { etat.points = []; toutMettreAJour(); }
function fermerPolygone() { if (etat.points.length >= 3) ouvrirFormulaire(); }

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
  let id, type;
  if (suivante) {
    [id, type] = suivante;
  } else {
    let n = 1;
    while (utilises.has(`P${n}`)) n++;
    [id, type] = [`P${n}`, "normale"];
  }
  champId.value = id;
  champType.value = type;
  champDuree.value = DUREE_PAR_DEFAUT[type];
}

function ouvrirFormulaire() {
  etat.formulaireOuvert = true;
  formulaire.hidden = false;
  messageFormulaire.textContent = "";
  proposerValeurs();
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
  const erreur = verifierId(id);
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
  fermerFormulaire();
  toutMettreAJour();
});

// « Modifier le tracé » : on garde les points pour pouvoir les corriger.
el("btn-abandonner").addEventListener("click", () => { fermerFormulaire(); toutMettreAJour(); });

// ---------- Liste ----------
function supprimer(cat, id) {
  if (cat === "zone") etat.zones = etat.zones.filter((z) => z.id !== id);
  else etat.places = etat.places.filter((p) => p.id !== id);
  toutMettreAJour();
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
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "bouton-secondaire petit";
    btn.textContent = "Supprimer";
    btn.setAttribute("aria-label", `Supprimer ${e.id}`);
    btn.addEventListener("click", () => supprimer(e.cat, e.id));
    li.append(pastille, texte, btn);
    liste.append(li);
  }
  compteur.textContent = `${etat.places.length} place(s), ${etat.zones.length} zone(s) interdite(s).`;
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

// ---------- Export ----------
btnExporter.addEventListener("click", () => {
  const donnees = {
    parking: PARKING,
    resolution: [canvas.width, canvas.height],
    places: trier(etat.places).map(({ id, type, duree_max_min, poly }) => ({ id, type, duree_max_min, poly })),
    zones_interdites: trier(etat.zones).map(({ id, poly }) => ({ id, poly })),
  };
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

// ---------- Mise à jour générale ----------
function majAide() {
  const n = etat.points.length;
  if (!etat.image) aide.textContent = "Commence par choisir la photo de référence de la maquette.";
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
  btnExporter.disabled = !etat.image || ouvert || etat.places.length + etat.zones.length === 0;
}

function toutMettreAJour() {
  dessiner();
  afficherListe();
  majBoutons();
  majAide();
}

toutMettreAJour();