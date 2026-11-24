// SVG map of the parking, built from the floor plan of GET /api/parking (contract docs/contrats.md §4.3).
// 1 SVG unit = 1 cm. Each spot is a <g data-spot="A3"> (convention of shared/js/realtime.js, which adds the
// state class free / occupied / reserved). Special spots (accessible, electric) carry the class "special"
// and a label, so they stay recognisable whatever their state.
//
//   import { drawParkingPlan, DEFAULT_LAYOUT } from './parking-plan.js';
//   const svg = drawParkingPlan(container, layout);
//
// The geometry (lane, landmarks, labels) is computed by pure functions, tested without a browser
// (frontend/tools/test_parking_plan.mjs).

const SVG_NS = 'http://www.w3.org/2000/svg';

export const TYPE_LABELS = { accessible: 'PMR', electric: 'ÉLEC' };
const TYPE_NAMES = { accessible: 'PMR', electric: 'électrique' };

// Plan of the scale model (contract §4.1 and §4.3), used when GET /api/parking is not available
// (fake API, server without plan yet).
export const DEFAULT_LAYOUT = {
  parking: 'P1',
  name: "Maquette SUP'COM",
  dimensions_cm: [60, 40],
  spots: [
    { id: 'A1', type: 'accessible', rect_cm: [14, 0, 8, 13], node: 'A1' },
    { id: 'A2', type: 'standard', rect_cm: [22, 0, 8, 13], node: 'A2' },
    { id: 'A3', type: 'standard', rect_cm: [30, 0, 8, 13], node: 'A3' },
    { id: 'A4', type: 'standard', rect_cm: [38, 0, 8, 13], node: 'A4' },
    { id: 'B1', type: 'standard', rect_cm: [14, 27, 8, 13], node: 'B1' },
    { id: 'B2', type: 'standard', rect_cm: [22, 27, 8, 13], node: 'B2' },
    { id: 'B3', type: 'standard', rect_cm: [30, 27, 8, 13], node: 'B3' },
    { id: 'B4', type: 'electric', rect_cm: [38, 27, 8, 13], node: 'B4' },
  ],
  forbidden_zones: [{ id: 'allee1', rect_cm: [50, 13, 10, 14] }],
  graph: null,
};

const isRect = (r) => Array.isArray(r) && r.length === 4 && r.every(Number.isFinite) && r[2] > 0 && r[3] > 0;

/** Spots that can be drawn (with a valid rect_cm), sorted by id. */
export function drawableSpots(layout) {
  return (layout?.spots ?? []).filter((s) => s && typeof s.id === 'string' && isRect(s.rect_cm))
    .sort((a, b) => a.id.localeCompare(b.id));
}

/** True if the plan can be drawn: positive dimensions and at least one spot with a rectangle. */
export function isDrawable(layout) {
  const d = layout?.dimensions_cm;
  return Array.isArray(d) && d.length === 2 && d.every((v) => Number.isFinite(v) && v > 0)
    && drawableSpots(layout).length > 0;
}

/**
 * Driving lane between the two rows of spots: { y, height } in cm.
 * Rows = spots above / below the middle of the parking. Without two rows, a band in the middle.
 */
export function laneBand(layout) {
  const [, height] = layout.dimensions_cm;
  const middle = height / 2;
  const spots = drawableSpots(layout);
  const upper = spots.filter((s) => s.rect_cm[1] + s.rect_cm[3] / 2 < middle);
  const lower = spots.filter((s) => s.rect_cm[1] + s.rect_cm[3] / 2 >= middle);
  if (upper.length === 0 || lower.length === 0) {
    return { y: middle - height / 6, height: height / 3 };
  }
  const top = Math.max(...upper.map((s) => s.rect_cm[1] + s.rect_cm[3]));
  const bottom = Math.min(...lower.map((s) => s.rect_cm[1]));
  return bottom > top ? { y: top, height: bottom - top } : { y: middle - height / 6, height: height / 3 };
}

/**
 * Entrance and exit labels: the "entrance" / "exit" nodes of the graph when it is there (sprint 7),
 * otherwise the left and right ends of the lane (contract §4.1: enter on the left, leave on the right).
 */
export function landmarks(layout) {
  const [width] = layout.dimensions_cm;
  const lane = laneBand(layout);
  const y = lane.y + lane.height / 2;
  const nodes = layout.graph?.nodes ?? [];
  const entrance = nodes.find((n) => n.type === 'entrance');
  const exit = nodes.find((n) => n.type === 'exit');
  const margin = Math.min(5.5, width / 10);
  return [
    { label: 'ENTRÉE', x: entrance ? Math.max(entrance.x, margin) : margin, y: entrance?.y ?? y },
    { label: 'SORTIE', x: exit ? Math.min(exit.x, width - margin) : width - margin, y: exit?.y ?? y },
  ];
}

/** Text of the spot tooltip, e.g. "A1 (PMR)". The live map adds " : libre". */
export function spotTitle(spot) {
  const name = TYPE_NAMES[spot.type];
  return name ? `${spot.id} (${name})` : spot.id;
}

/** Accessible description of the whole map (screen readers). */
export function planDescription(layout) {
  const spots = drawableSpots(layout);
  const ids = spots.map((s) => s.id);
  const special = spots.filter((s) => TYPE_NAMES[s.type]).map((s) => `${s.id} ${TYPE_NAMES[s.type]}`);
  return `Plan du parking ${layout.parking ?? ''} : ${spots.length} places (${ids.join(', ')})`
    + (special.length ? `, dont ${special.join(' et ')}` : '');
}

function element(doc, name, attributes = {}, text = '') {
  const e = doc.createElementNS(SVG_NS, name);
  for (const [k, v] of Object.entries(attributes)) e.setAttribute(k, String(v));
  if (text) e.textContent = text;
  return e;
}

/**
 * Draws the map in {@code container} (replacing its content) and returns the <svg>.
 * Throws an Error if the plan cannot be drawn (see isDrawable).
 */
export function drawParkingPlan(container, layout, doc = globalThis.document) {
  if (!isDrawable(layout)) throw new Error('plan du parking incomplet (dimensions ou rect_cm manquants)');
  const [width, height] = layout.dimensions_cm;
  const lane = laneBand(layout);
  const svg = element(doc, 'svg', { viewBox: `0 0 ${width} ${height}`, role: 'img', 'aria-labelledby': 'map-title' });
  svg.append(
    element(doc, 'title', { id: 'map-title' }, planDescription(layout)),
    element(doc, 'rect', { class: 'ground', x: 0, y: 0, width, height, rx: 1 }),
    element(doc, 'rect', { class: 'lane', x: 0, y: lane.y, width, height: lane.height }),
  );
  for (const z of layout.forbidden_zones ?? []) {
    if (!isRect(z?.rect_cm)) continue;
    const [x, y, w, h] = z.rect_cm;
    const g = element(doc, 'g', { class: 'forbidden', 'data-zone': z.id });
    g.append(
      element(doc, 'title', {}, `Zone interdite ${z.id} : arrêt interdit`),
      element(doc, 'rect', { x, y, width: w, height: h }),
    );
    svg.append(g);
  }
  for (const m of landmarks(layout)) {
    svg.append(element(doc, 'text', { class: 'landmark', x: m.x, y: m.y }, m.label));
  }
  for (const s of drawableSpots(layout)) {
    const [x, y, w, h] = s.rect_cm;
    const typeLabel = TYPE_LABELS[s.type];
    const g = element(doc, 'g', { 'data-spot': s.id, 'data-type': s.type ?? 'standard', class: typeLabel ? 'special' : '' });
    g.append(
      element(doc, 'title', {}, spotTitle(s)),
      element(doc, 'rect', { x, y, width: w, height: h }),
      element(doc, 'text', { class: 'spot-number', x: x + w / 2, y: y + h * (typeLabel ? 0.4 : 0.5) }, s.id),
    );
    if (typeLabel) {
      g.append(element(doc, 'text', { class: 'spot-type', x: x + w / 2, y: y + h * 0.72 }, typeLabel));
    }
    svg.append(g);
  }
  container.replaceChildren(svg);
  return svg;
}
