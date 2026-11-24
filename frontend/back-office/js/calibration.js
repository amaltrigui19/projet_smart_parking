// Spot calibration (back office): draw the polygons on the reference photo and save places.json
// in the format of the contract (docs/contrats.md §5.2) with GET and PUT /api/config/polygons (§2.10).
import { callApi, currentUser } from '/shared/js/session.js';

const PARKING = "P1";
const PI_RESOLUTION = [640, 480];
const DEFAULT_DURATION = { standard: 120, accessible: 120, electric: 60 };
// Validated plan (docs/contrats.md §4): only used to suggest the next name and type.
const PLAN = [
  ["A1", "accessible"], ["A2", "standard"], ["A3", "standard"], ["A4", "standard"],
  ["B1", "standard"], ["B2", "standard"], ["B3", "standard"], ["B4", "electric"],
];
const COLORS = { standard: "#3fb3b3", accessible: "#5b8def", electric: "#e3b341", zone: "#e5534b" };
const PLAN_IDS = PLAN.map(([id]) => id);
const POLYGONS_API = "/api/config/polygons";

// resolution: [width, height] of the photo, or of the file loaded from the server.
// editing: element taken out of the list while it is being modified (put back if cancelled).
const state = {
  image: null, resolution: null, points: [], spots: [], zones: [],
  formOpen: false, editing: null, unsaved: false, sending: false,
};

const el = (id) => document.getElementById(id);
const canvas = el("canvas");
const ctx = canvas.getContext("2d");
const photoInput = el("photo-input");
const emptyState = el("empty-state");
const hint = el("hint");
const resolutionInfo = el("resolution-info");
const undoPointButton = el("undo-point-button");
const closePolygonButton = el("close-polygon-button");
const clearButton = el("clear-button");
const form = el("element-form");
const idInput = el("id-input");
const typeInput = el("type-input");
const durationInput = el("duration-input");
const spotFields = el("spot-fields");
const formMessage = el("form-message");
const list = el("element-list");
const counter = el("counter");
const exportButton = el("export-button");
const saveButton = el("save-button");
const serverMessage = el("server-message");

const clamp = (v, min, max) => Math.min(max, Math.max(min, v));
const sortById = (t) => [...t].sort((a, b) => a.id.localeCompare(b.id, "fr", { numeric: true }));
const usedIds = () => new Set([...state.spots, ...state.zones].map((e) => e.id));
const centre = (polygon) => [
  polygon.reduce((s, p) => s + p[0], 0) / polygon.length,
  polygon.reduce((s, p) => s + p[1], 0) / polygon.length,
];

// ---------- Photo ----------
photoInput.addEventListener("change", () => {
  const file = photoInput.files[0];
  if (!file) return;
  const url = URL.createObjectURL(file);
  const img = new Image();
  img.onload = () => {
    cancelEditing(); // an element being modified gets its place back before any decision
    // The known resolution comes from a previous photo or from the file loaded from the server.
    const res = state.resolution;
    const sameSize = res !== null && img.naturalWidth === res[0] && img.naturalHeight === res[1];
    const hasElements = state.spots.length + state.zones.length > 0;
    if (hasElements && !sameSize &&
        !confirm(`Cette photo (${img.naturalWidth} × ${img.naturalHeight}) n'a pas la même résolution que les ` +
                 `polygones (${res[0]} × ${res[1]}) : ils seront effacés. Continuer ?`)) {
      URL.revokeObjectURL(url);
      photoInput.value = "";
      return;
    }
    if (!sameSize && hasElements) { state.spots = []; state.zones = []; state.unsaved = true; }
    if (state.image) URL.revokeObjectURL(state.image.src);
    state.image = img;
    state.resolution = [img.naturalWidth, img.naturalHeight];
    state.points = [];
    canvas.width = img.naturalWidth;
    canvas.height = img.naturalHeight;
    canvas.hidden = false;
    emptyState.hidden = true;
    showResolution();
    closeForm();
    refresh();
  };
  img.onerror = () => {
    URL.revokeObjectURL(url);
    hint.textContent = "Ce fichier n'est pas une image lisible. Choisis une photo JPG ou PNG.";
  };
  img.src = url;
});

function showResolution() {
  const [w, h] = [canvas.width, canvas.height];
  const ok = w === PI_RESOLUTION[0] && h === PI_RESOLUTION[1];
  resolutionInfo.textContent = ok
    ? `Résolution de la photo : ${w} × ${h} px.`
    : `Résolution de la photo : ${w} × ${h} px. Le Pi analyse en ${PI_RESOLUTION[0]} × ${PI_RESOLUTION[1]} : ` +
      "prends la photo de référence avec le Pi à cette résolution, sinon les polygones seront décalés.";
  resolutionInfo.className = ok ? "info" : "info warning";
}

// ---------- Drawing ----------
// The photo may be displayed smaller than its real size: convert the click position into pixels of the real photo.
function positionInImage(evt) {
  const r = canvas.getBoundingClientRect();
  const x = Math.round(((evt.clientX - r.left) / r.width) * canvas.width);
  const y = Math.round(((evt.clientY - r.top) / r.height) * canvas.height);
  return [clamp(x, 0, canvas.width - 1), clamp(y, 0, canvas.height - 1)];
}

canvas.addEventListener("pointerdown", (evt) => {
  if (!state.image || state.formOpen) return;
  evt.preventDefault();
  state.points.push(positionInImage(evt));
  refresh();
});

// If an existing element was being modified, it gets its place back unchanged.
function cancelEditing() {
  if (!state.editing) return;
  const { category, element } = state.editing;
  (category === "zone" ? state.zones : state.spots).push(element);
  state.editing = null;
  state.points = [];
}

function undoPoint() { state.points.pop(); refresh(); }
function clearOutline() { state.points = []; cancelEditing(); refresh(); }
function closePolygon() {
  if (state.points.length < 3) return;
  // When an existing element is retouched, the form takes its values again.
  openForm(state.editing ? { category: state.editing.category, ...state.editing.element } : null);
}

undoPointButton.addEventListener("click", undoPoint);
clearButton.addEventListener("click", clearOutline);
closePolygonButton.addEventListener("click", closePolygon);

document.addEventListener("keydown", (evt) => {
  if (!state.image || state.formOpen) return;
  if (evt.target.closest("input, select, textarea, button, label")) return;
  if (evt.key === "Enter") { evt.preventDefault(); closePolygon(); }
  else if (evt.key === "Backspace") { evt.preventDefault(); undoPoint(); }
  else if (evt.key === "Escape") { clearOutline(); }
});

// ---------- Form ----------
const category = () => form.elements.category.value; // "spot" or "zone"

function suggestValues() {
  const used = usedIds();
  const zone = category() === "zone";
  spotFields.hidden = zone;
  if (zone) {
    let n = 1;
    while (used.has(`allee${n}`)) n++;
    idInput.value = `allee${n}`;
    return;
  }
  const next = PLAN.find(([id]) => !used.has(id));
  if (!next) {
    // All spots of the plan are drawn: the server refuses a spot outside the plan (contract §2.10).
    idInput.value = "";
    formMessage.textContent = `Toutes les places du plan (${PLAN_IDS.join(", ")}) sont déjà dessinées. ` +
      "Supprime ou modifie une place existante, ou choisis « zone interdite ».";
    return;
  }
  const [id, type] = next;
  idInput.value = id;
  typeInput.value = type;
  durationInput.value = DEFAULT_DURATION[type];
}

// values: existing element to modify ({ category, id, type, max_duration_min }), otherwise the next ones are suggested.
function openForm(values = null) {
  state.formOpen = true;
  form.hidden = false;
  formMessage.textContent = "";
  if (values) {
    form.elements.category.value = values.category;
    spotFields.hidden = values.category === "zone";
    idInput.value = values.id;
    if (values.category === "spot") {
      typeInput.value = values.type;
      durationInput.value = values.max_duration_min;
    }
  } else {
    suggestValues();
  }
  // Without a photo, points cannot be retouched: only the name, the type and the duration.
  el("edit-outline-button").disabled = !state.image;
  refresh();
  idInput.focus();
  idInput.select();
}

function closeForm() {
  state.formOpen = false;
  form.hidden = true;
}

form.addEventListener("change", (evt) => {
  if (evt.target.name === "category") suggestValues();
  if (evt.target === typeInput) durationInput.value = DEFAULT_DURATION[typeInput.value];
});

function checkId(id) {
  if (!id) return "Donne un nom à cet élément, par exemple A1 ou allee1.";
  if (!/^[A-Za-z0-9_-]+$/.test(id)) return "Le nom ne peut contenir que des lettres sans accent, des chiffres, - et _.";
  if (usedIds().has(id)) return `Le nom ${id} est déjà utilisé.`;
  return "";
}

form.addEventListener("submit", (evt) => {
  evt.preventDefault();
  const id = idInput.value.trim();
  const error = checkId(id) || (category() === "spot" && !PLAN_IDS.includes(id)
    ? `La place ${id} n'existe pas dans le plan du parking (${PLAN_IDS.join(", ")}).` : "");
  if (error) { formMessage.textContent = error; idInput.focus(); return; }
  const polygon = state.points.map(([x, y]) => [x, y]);
  if (category() === "zone") {
    state.zones.push({ id, polygon });
  } else {
    const duration = Number(durationInput.value);
    if (!Number.isInteger(duration) || duration < 1) {
      formMessage.textContent = "La durée maximale doit être un nombre entier de minutes, au moins 1.";
      durationInput.focus();
      return;
    }
    state.spots.push({ id, type: typeInput.value, max_duration_min: duration, polygon });
  }
  state.points = [];
  state.editing = null;
  state.unsaved = true;
  closeForm();
  refresh();
});

// "Modifier le tracé": keep the points so they can be corrected.
el("edit-outline-button").addEventListener("click", () => { closeForm(); refresh(); });

// "Annuler": drop this polygon (an existing element gets its place back, unchanged).
el("cancel-form-button").addEventListener("click", () => {
  state.points = [];
  cancelEditing();
  closeForm();
  refresh();
});

// ---------- List ----------
function remove(cat, id) {
  if (cat === "zone") state.zones = state.zones.filter((z) => z.id !== id);
  else state.spots = state.spots.filter((s) => s.id !== id);
  state.unsaved = true;
  refresh();
}

// "Modifier": the element leaves the list, its points become the current outline and the form opens with its
// values. "Annuler" puts it back unchanged.
function edit(cat, id) {
  if (state.formOpen) return;
  cancelEditing();
  const array = cat === "zone" ? state.zones : state.spots;
  const element = array.find((e) => e.id === id);
  if (!element) return;
  array.splice(array.indexOf(element), 1);
  state.editing = { category: cat, element };
  state.points = element.polygon.map(([x, y]) => [x, y]);
  openForm({ category: cat, ...element });
}

function smallButton(text, label, onClick, disabled = false) {
  const b = document.createElement("button");
  b.type = "button";
  b.className = "button-secondary small";
  b.textContent = text;
  b.setAttribute("aria-label", label);
  b.disabled = disabled;
  b.addEventListener("click", onClick);
  return b;
}

function showList() {
  list.replaceChildren();
  const elements = [
    ...sortById(state.spots).map((s) => ({ ...s, category: "spot" })),
    ...sortById(state.zones).map((z) => ({ ...z, category: "zone" })),
  ];
  for (const e of elements) {
    const li = document.createElement("li");
    const swatch = document.createElement("span");
    swatch.className = "color-swatch";
    swatch.style.background = e.category === "zone" ? COLORS.zone : COLORS[e.type];
    const text = document.createElement("span");
    text.className = "item-text";
    text.textContent = e.category === "zone"
      ? `${e.id}, zone interdite`
      : `${e.id}, ${e.type}, ${e.max_duration_min} min`;
    li.append(
      swatch, text,
      smallButton("Modifier", `Modifier ${e.id}`, () => edit(e.category, e.id), state.formOpen),
      smallButton("Supprimer", `Supprimer ${e.id}`, () => remove(e.category, e.id)),
    );
    list.append(li);
  }
  counter.textContent = `${state.spots.length} place(s), ${state.zones.length} zone(s) interdite(s).` +
    (state.unsaved ? " Modifications non enregistrées sur le serveur." : "");
}

// ---------- Canvas ----------
function drawPolygon(polygon, color, name, lineWidth, fontSize) {
  ctx.save();
  ctx.beginPath();
  polygon.forEach(([x, y], i) => (i ? ctx.lineTo(x, y) : ctx.moveTo(x, y)));
  ctx.closePath();
  ctx.globalAlpha = 0.28;
  ctx.fillStyle = color;
  ctx.fill();
  ctx.globalAlpha = 1;
  ctx.lineWidth = lineWidth;
  ctx.strokeStyle = color;
  ctx.stroke();
  const [cx, cy] = centre(polygon);
  ctx.font = `600 ${fontSize}px system-ui, sans-serif`;
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  ctx.lineWidth = Math.max(3, fontSize / 5);
  ctx.strokeStyle = "rgba(0, 0, 0, .75)";
  ctx.strokeText(name, cx, cy);
  ctx.fillStyle = "#ffffff";
  ctx.fillText(name, cx, cy);
  ctx.restore();
}

function draw() {
  if (!state.image) return;
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  ctx.drawImage(state.image, 0, 0);
  const lineWidth = Math.max(2, canvas.width / 320);
  const fontSize = Math.max(12, Math.round(canvas.width / 40));
  for (const z of state.zones) drawPolygon(z.polygon, COLORS.zone, z.id, lineWidth, fontSize);
  for (const s of state.spots) drawPolygon(s.polygon, COLORS[s.type], s.id, lineWidth, fontSize);

  if (!state.points.length) return;
  ctx.save();
  ctx.strokeStyle = "#ffffff";
  ctx.lineWidth = lineWidth;
  ctx.setLineDash([lineWidth * 3, lineWidth * 2]);
  ctx.beginPath();
  state.points.forEach(([x, y], i) => (i ? ctx.lineTo(x, y) : ctx.moveTo(x, y)));
  if (state.points.length >= 3) ctx.closePath();
  ctx.stroke();
  ctx.setLineDash([]);
  for (const [x, y] of state.points) {
    ctx.beginPath();
    ctx.arc(x, y, lineWidth * 2, 0, Math.PI * 2);
    ctx.fillStyle = "#ffffff";
    ctx.fill();
    ctx.lineWidth = 1;
    ctx.strokeStyle = "#000000";
    ctx.stroke();
  }
  ctx.restore();
}

// ---------- places.json file (contract §5.2) ----------
function buildFile() {
  return {
    parking: PARKING,
    resolution: [...state.resolution],
    spots: sortById(state.spots).map(({ id, type, max_duration_min, polygon }) => ({ id, type, max_duration_min, polygon })),
    forbidden_zones: sortById(state.zones).map(({ id, polygon }) => ({ id, polygon })),
  };
}

// ---------- Export ----------
exportButton.addEventListener("click", () => {
  const data = buildFile();
  const blob = new Blob([JSON.stringify(data, null, 2) + "\n"], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = "places.json";
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
});

// ---------- Server: GET and PUT /api/config/polygons (contract §2.10) ----------

// Shows a message in the panel. kind: "success", "error" or "info". details: list of lines.
function showMessage(kind, text, details = []) {
  serverMessage.className = `server-message ${kind}`;
  serverMessage.setAttribute("role", kind === "error" ? "alert" : "status");
  serverMessage.replaceChildren();
  const p = document.createElement("p");
  p.textContent = text;
  serverMessage.append(p);
  if (details.length) {
    const ul = document.createElement("ul");
    for (const d of details) {
      const li = document.createElement("li");
      li.textContent = d;
      ul.append(li);
    }
    serverMessage.append(ul);
  }
}

// Messages shared by GET and PUT for 403, unreachable server and other errors (a 401 already sends back
// to the login page, see shared/js/session.js).
function commonErrorMessage({ status, body }) {
  if (status === 0) return "Serveur injoignable : vérifie qu'il est lancé (ou la fausse API), puis réessaie.";
  if (status === 403) return "Accès refusé (403) : cette page est réservée aux opérateurs.";
  const detail = body && body.message ? ` : ${body.message}` : "";
  return `Erreur du serveur (${status}${body && body.error ? `, ${body.error}` : ""})${detail}`;
}

// When the page opens: load the polygons already saved, if any.
async function loadFromServer() {
  showMessage("info", "Recherche des polygones déjà enregistrés…");
  const r = await callApi(POLYGONS_API);
  if (r.status === 404) {
    showMessage("info", "Aucun fichier enregistré sur le serveur : on part d'un plan vide.");
    return;
  }
  if (r.status !== 200) { showMessage("error", commonErrorMessage(r)); return; }
  const d = r.body;
  if (!d || !Array.isArray(d.spots) || !Array.isArray(d.forbidden_zones) || !Array.isArray(d.resolution)) {
    showMessage("error", "Le serveur a renvoyé un fichier de polygones illisible : on part d'un plan vide.");
    return;
  }
  if (state.spots.length + state.zones.length > 0) {
    showMessage("info", "Des polygones existent sur le serveur, mais tu as déjà commencé à dessiner : " +
      "recharge la page pour les afficher.");
    return;
  }
  state.spots = d.spots.map(({ id, type, max_duration_min, polygon }) => ({ id, type, max_duration_min, polygon }));
  state.zones = d.forbidden_zones.map(({ id, polygon }) => ({ id, polygon }));
  state.resolution = [d.resolution[0], d.resolution[1]];
  state.unsaved = false;
  showMessage("success", `${state.spots.length} place(s) et ${state.zones.length} zone(s) chargées depuis le ` +
    `serveur. Choisis la photo de référence (${state.resolution[0]} × ${state.resolution[1]}) pour les voir et les modifier.`);
  refresh();
}

// "Enregistrer sur le serveur": local validation (same rules as the server), then PUT of the whole file.
saveButton.addEventListener("click", async () => {
  const data = buildFile();
  const errors = window.validateSpots(data, PLAN_IDS);
  if (errors.length) {
    showMessage("error", `Le fichier n'est pas valide (${errors.length} problème(s)), rien n'a été envoyé :`, errors);
    return;
  }
  state.sending = true;
  updateButtons();
  showMessage("info", "Envoi au serveur…");
  const r = await callApi(POLYGONS_API, { method: "PUT", body: data });
  state.sending = false;
  if (r.status === 200) {
    state.unsaved = false;
    const time = new Date().toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" });
    showMessage("success", `Enregistré sur le serveur à ${time} : ${data.spots.length} place(s), ` +
      `${data.forbidden_zones.length} zone(s) interdite(s).`);
  } else if (r.status === 400 && r.body && r.body.error === "invalid_config") {
    showMessage("error", "Le serveur a refusé le fichier (invalid_config) :",
      String(r.body.message || "").split(" ; ").filter(Boolean));
  } else {
    showMessage("error", commonErrorMessage(r));
  }
  refresh();
});

// ---------- General refresh ----------
function updateHint() {
  const n = state.points.length;
  if (!state.image && state.spots.length + state.zones.length > 0) {
    hint.textContent = "Choisis la photo de référence pour voir et modifier les polygones chargés.";
  } else if (!state.image) hint.textContent = "Commence par choisir la photo de référence de la maquette.";
  else if (state.formOpen) hint.textContent = "Donne un nom et un type à ce polygone, puis enregistre-le.";
  else if (n === 0) hint.textContent = "Clique sur les coins d'une place, dans l'ordre, en faisant le tour.";
  else if (n < 3) hint.textContent = `${n} point(s) placé(s). Il en faut au moins 3.`;
  else hint.textContent = `${n} points placés. Clique sur « Fermer le polygone » (ou appuie sur Entrée) quand le tour est fait.`;
}

function updateButtons() {
  const n = state.points.length;
  const open = state.formOpen;
  undoPointButton.disabled = n === 0 || open;
  clearButton.disabled = n === 0 || open;
  closePolygonButton.disabled = n < 3 || open;
  const nothing = state.resolution === null || open || state.spots.length + state.zones.length === 0;
  exportButton.disabled = nothing;
  saveButton.disabled = nothing || state.sending === true;
}

function refresh() {
  draw();
  showList();
  updateButtons();
  updateHint();
}

// Without a valid operator session, protected-page.js already sends back to the login page.
if (currentUser()) {
  refresh();
  loadFromServer();
}
