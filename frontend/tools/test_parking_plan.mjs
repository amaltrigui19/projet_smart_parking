// Tests of shared/js/parking-plan.js (pure geometry, no browser): node frontend/tools/test_parking_plan.mjs
import assert from 'node:assert/strict';
import {
  DEFAULT_LAYOUT, drawableSpots, isDrawable, laneBand, landmarks, spotTitle, planDescription, drawParkingPlan,
} from '../shared/js/parking-plan.js';

let passed = 0;
const test = (name, fn) => { fn(); passed++; console.log(`ok - ${name}`); };

test('le plan par défaut est dessinable avec 8 places', () => {
  assert.ok(isDrawable(DEFAULT_LAYOUT));
  assert.deepEqual(drawableSpots(DEFAULT_LAYOUT).map((s) => s.id), ['A1', 'A2', 'A3', 'A4', 'B1', 'B2', 'B3', 'B4']);
});

test('une place sans rect_cm est ignorée, un plan sans dimensions refusé', () => {
  const layout = { ...DEFAULT_LAYOUT, spots: [...DEFAULT_LAYOUT.spots, { id: 'C1', type: 'standard', rect_cm: null }] };
  assert.equal(drawableSpots(layout).length, 8);
  assert.equal(isDrawable({ ...DEFAULT_LAYOUT, dimensions_cm: null }), false);
  assert.equal(isDrawable({ ...DEFAULT_LAYOUT, spots: [] }), false);
  assert.equal(isDrawable(null), false);
});

test("l'allée est entre les deux rangées (y 13 à 27)", () => {
  assert.deepEqual(laneBand(DEFAULT_LAYOUT), { y: 13, height: 14 });
});

test('une seule rangée : allée au milieu', () => {
  const layout = { ...DEFAULT_LAYOUT, spots: DEFAULT_LAYOUT.spots.filter((s) => s.id.startsWith('A')) };
  const lane = laneBand(layout);
  assert.ok(lane.y > 0 && lane.y + lane.height < 40);
});

test('entrée à gauche, sortie à droite, sur l\'allée', () => {
  const [entrance, exit] = landmarks(DEFAULT_LAYOUT);
  assert.equal(entrance.label, 'ENTRÉE');
  assert.equal(exit.label, 'SORTIE');
  assert.ok(entrance.x < exit.x);
  assert.equal(entrance.y, 20);
});

test('les nœuds entrance/exit du graphe sont utilisés quand ils existent', () => {
  const graph = { nodes: [{ id: 'E', type: 'entrance', x: 8, y: 18 }, { id: 'S', type: 'exit', x: 52, y: 22 }] };
  const [entrance, exit] = landmarks({ ...DEFAULT_LAYOUT, graph });
  assert.deepEqual([entrance.x, entrance.y, exit.x, exit.y], [8, 18, 52, 22]);
});

test('titres et description des places spéciales', () => {
  assert.equal(spotTitle({ id: 'A1', type: 'accessible' }), 'A1 (PMR)');
  assert.equal(spotTitle({ id: 'B4', type: 'electric' }), 'B4 (électrique)');
  assert.equal(spotTitle({ id: 'A2', type: 'standard' }), 'A2');
  assert.match(planDescription(DEFAULT_LAYOUT), /8 places .*A1 PMR et B4 électrique/);
});

// Minimal DOM, just enough for drawParkingPlan.
function fakeDocument() {
  const make = (name) => ({
    name, attributes: {}, children: [], textContent: '',
    setAttribute(k, v) { this.attributes[k] = v; },
    append(...c) { this.children.push(...c); },
    replaceChildren(...c) { this.children = c; },
  });
  return { createElementNS: (_ns, name) => make(name), make };
}
const all = (node) => [node, ...node.children.flatMap(all)];

test('drawParkingPlan dessine une <g data-spot> par place, avec la classe special', () => {
  const doc = fakeDocument();
  const container = doc.make('div');
  const svg = drawParkingPlan(container, DEFAULT_LAYOUT, doc);
  assert.equal(container.children[0], svg);
  assert.equal(svg.attributes.viewBox, '0 0 60 40');
  const spots = all(svg).filter((n) => n.attributes['data-spot']);
  assert.equal(spots.length, 8);
  const special = spots.filter((n) => n.attributes.class === 'special').map((n) => n.attributes['data-spot']);
  assert.deepEqual(special, ['A1', 'B4']);
  assert.ok(all(svg).some((n) => n.attributes['data-zone'] === 'allee1'));
  assert.equal(all(svg).filter((n) => n.attributes.class === 'landmark').length, 2);
});

test('drawParkingPlan refuse un plan incomplet', () => {
  assert.throws(() => drawParkingPlan(fakeDocument().make('div'), { dimensions_cm: [60, 40], spots: [] }, fakeDocument()));
});

console.log(`\n${passed} tests OK`);
