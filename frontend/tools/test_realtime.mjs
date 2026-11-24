// Tests of frontend/shared/js/realtime.js (contract docs/contrats.md §1.4), with a fake WebSocket and a fake timer.
// Run from the root of the repository: node frontend/tools/test_realtime.mjs
import assert from 'node:assert/strict';
import { startRealtime, webSocketUrl, colorSpot, RECONNECT_DELAYS_MS, STATUS } from '../shared/js/realtime.js';

// ---------- Test doubles ----------
class FakeWebSocket {
  static instances = [];
  constructor(url) { this.url = url; this.closed = false; FakeWebSocket.instances.push(this); }
  open() { this.onopen?.(); }
  receive(obj) { this.onmessage?.({ data: typeof obj === 'string' ? obj : JSON.stringify(obj) }); }
  drop(code = 1006) { this.closed = true; this.onclose?.({ code }); }
  close(code = 1000) { if (!this.closed) this.drop(code); }
}
const last = () => FakeWebSocket.instances.at(-1);

const FULL_STATE = {
  parking: 'P1',
  spots: [
    { id: 'A1', type: 'accessible', state: 'free', since: '2026-11-20T08:00:00Z', max_duration_min: 120 },
    { id: 'A3', type: 'standard', state: 'free', since: '2026-11-20T10:00:00Z', max_duration_min: 120 },
    { id: 'B4', type: 'electric', state: 'reserved', since: '2026-11-20T09:58:40Z', max_duration_min: 60 },
  ],
};
const ok = (body = FULL_STATE) => ({ status: 200, body: structuredClone(body) });
const settle = () => new Promise((r) => setTimeout(r, 0)); // lets the internal awaits finish

function mount(overrides = {}) {
  FakeWebSocket.instances = [];
  const log = { changes: [], statuses: [], others: [], expirations: 0, loads: 0, timers: [] };
  const responses = overrides.responses || [];
  const rt = startRealtime({
    WebSocket: FakeWebSocket,
    readToken: () => ('token' in overrides ? overrides.token : 'test-token'),
    url: (t) => `ws://test/api/ws/spots?token=${t}`,
    loadSpots: async () => { log.loads += 1; return responses.length ? responses.shift() : ok(); },
    onSessionExpired: () => { log.expirations += 1; },
    isOnline: () => overrides.online ?? true,
    timer: {
      set: (f, ms) => { log.timers.push({ f, ms }); return log.timers.length; },
      clear: () => {},
    },
    onChange: (spot, state) => log.changes.push(`${spot}:${state}`),
    onStatus: (s, d) => log.statuses.push(d.delay ? `${s}(${d.delay})` : s),
    onOtherMessage: (m) => log.others.push(m.type),
  });
  const tick = () => log.timers.at(-1).f();
  return { rt, log, tick };
}

// ---------- Tests ----------
const tests = [];
const test = (name, f) => tests.push([name, f]);

test('URL du WebSocket : wss en HTTPS, ws en HTTP, jeton encodé', () => {
  assert.equal(webSocketUrl('a.b c', { protocol: 'https:', host: 'exemple.tn' }),
    'wss://exemple.tn/api/ws/spots?token=a.b%20c');
  assert.equal(webSocketUrl('t', { protocol: 'http:', host: 'localhost:8000' }),
    'ws://localhost:8000/api/ws/spots?token=t');
});

test('démarrage : WebSocket avec le jeton, puis état complet, puis statut « connected »', async () => {
  const { rt, log } = mount();
  assert.equal(last().url, 'ws://test/api/ws/spots?token=test-token');
  assert.deepEqual(log.statuses, ['connecting']);
  last().open();
  await settle();
  assert.equal(log.loads, 1);
  assert.deepEqual(log.changes, ['A1:free', 'A3:free', 'B4:reserved']);
  assert.equal(rt.status, STATUS.CONNECTED);
  assert.equal(rt.spots.get('B4').type, 'electric');
});

test('un message { spot, state, since } met à jour la place', async () => {
  const { rt, log } = mount();
  last().open();
  await settle();
  last().receive({ spot: 'A3', state: 'occupied', since: '2026-11-20T10:02:11Z' });
  assert.equal(log.changes.at(-1), 'A3:occupied');
  assert.equal(rt.spots.get('A3').state, 'occupied');
  assert.equal(rt.spots.get('A3').since, '2026-11-20T10:02:11Z');
  assert.equal(rt.spots.get('A3').type, 'standard'); // the other fields are kept
});

test('messages ignorés : JSON invalide, état inconnu, place invalide, message plus ancien, ancien format', async () => {
  const { rt, log } = mount();
  last().open();
  await settle();
  const before = log.changes.length;
  last().receive('pas du JSON');
  last().receive({ spot: 'A3', state: 'parked', since: '2026-11-20T10:05:00Z' });
  last().receive({ spot: 'A3"]', state: 'occupied' });
  last().receive({ spot: 'A3', state: 'occupied', since: '2026-11-20T09:00:00Z' }); // before 10:00
  last().receive({ place: 'A3', etat: 'occupee', depuis: '2026-11-20T10:05:00Z' }); // old French format
  assert.equal(log.changes.length, before);
  assert.equal(rt.spots.get('A3').state, 'free');
});

test('message avec « type » (événement, statut du Pi) : transmis à onOtherMessage', async () => {
  const { log } = mount();
  last().open();
  await settle();
  last().receive({ type: 'event', event: { type: 'straddling', spots: ['A3', 'A4'] } });
  assert.deepEqual(log.others, ['event']);
});

test("messages reçus pendant le chargement de l'état complet : appliqués après lui", async () => {
  const { rt, log } = mount();
  last().open(); // load in progress (await)
  last().receive({ spot: 'A3', state: 'occupied', since: '2026-11-20T10:02:11Z' }); // newer
  last().receive({ spot: 'A1', state: 'occupied', since: '2026-11-20T07:00:00Z' }); // older: ignored
  await settle();
  assert.deepEqual(log.changes, ['A1:free', 'A3:free', 'B4:reserved', 'A3:occupied']);
  assert.equal(rt.spots.get('A1').state, 'free');
});

test('coupure : reconnexion avec délai croissant (1 s, 2 s, 5 s, 10 s, 10 s)', async () => {
  const { log, tick } = mount();
  last().open();
  await settle();
  last().drop(); // the server stops
  await settle();
  for (let i = 0; i < 4; i += 1) {
    tick(); // new attempt…
    last().drop(); // …refused (server still stopped)
    await settle();
  }
  assert.deepEqual(log.timers.map((t) => t.ms), [1000, 2000, 5000, 10000, 10000]);
  assert.deepEqual(RECONNECT_DELAYS_MS, [1000, 2000, 5000, 10000]);
  assert.ok(log.statuses.includes('reconnecting(1000)'));
  assert.equal(FakeWebSocket.instances.length, 5);
});

test("reconnexion réussie : l'état complet est rechargé et le délai repart à 1 s", async () => {
  const after = structuredClone(FULL_STATE);
  after.spots[0].state = 'occupied'; // A1 changed during the outage
  after.spots[0].since = '2026-11-20T10:30:00Z';
  const { rt, log, tick } = mount({ responses: [ok(), ok(), ok(after)] });
  last().open();
  await settle();
  last().drop();
  await settle();
  tick();
  last().drop(); // 1st attempt refused (2nd load: check through the REST API)
  await settle();
  tick();
  last().open(); // 2nd attempt succeeds: 3rd load
  await settle();
  assert.equal(log.loads, 3);
  assert.equal(rt.spots.get('A1').state, 'occupied');
  assert.equal(rt.status, STATUS.CONNECTED);
  last().drop();
  await settle();
  assert.equal(log.timers.at(-1).ms, 1000);
});

test('401 sur GET /api/spots : session expirée, plus de reconnexion', async () => {
  const { rt, log } = mount({ responses: [{ status: 401, body: { error: 'unauthenticated' } }] });
  last().open();
  await settle();
  assert.equal(log.expirations, 1);
  assert.equal(last().closed, true);
  assert.equal(log.timers.length, 0);
  assert.equal(rt.status, STATUS.OFFLINE);
});

test("poignée de main refusée puis 401 de l'API : session expirée", async () => {
  const { log } = mount({ responses: [{ status: 401, body: null }] });
  last().drop(); // refused before opening: the browser does not give the HTTP code
  await settle();
  assert.equal(log.expirations, 1);
  assert.equal(log.timers.length, 0);
});

test('fermeture 4401 par le serveur : session expirée', async () => {
  const { log } = mount();
  last().open();
  await settle();
  last().drop(4401);
  await settle();
  assert.equal(log.expirations, 1);
  assert.equal(log.timers.length, 0);
});

test('pas de jeton : retour à la connexion, aucun WebSocket', () => {
  const { log } = mount({ token: null });
  assert.equal(FakeWebSocket.instances.length, 0);
  assert.equal(log.expirations, 1);
});

test('serveur injoignable (API REST : statut 0) : statut « offline »', async () => {
  const { log } = mount({ responses: [{ status: 0, body: null }] });
  last().drop();
  await settle();
  assert.equal(log.statuses.at(-1), 'offline(1000)');
});

test('stop() : fermeture propre, pas de reconnexion', async () => {
  const { rt, log } = mount();
  last().open();
  await settle();
  rt.stop();
  await settle();
  assert.equal(last().closed, true);
  assert.equal(log.timers.length, 0);
});

test("colorSpot : une seule classe d'état sur [data-spot]", () => {
  const classes = new Set(['free']);
  const element = {
    classList: { remove: (...c) => c.forEach((x) => classes.delete(x)), add: (c) => classes.add(c) },
    setAttribute() {},
  };
  const root = { querySelector: (s) => (s === '[data-spot="A3"]' ? element : null) };
  assert.equal(colorSpot(root, 'A3', 'occupied'), true);
  assert.deepEqual([...classes], ['occupied']);
  assert.equal(colorSpot(root, 'Z9', 'free'), false);
  assert.equal(colorSpot(root, 'A3', 'parked'), false);
});

let failures = 0;
for (const [name, f] of tests) {
  try {
    await f();
    console.log(`ok   ${name}`);
  } catch (e) {
    failures += 1;
    console.log(`ÉCHEC ${name}\n     ${e.message.split('\n').join('\n     ')}`);
  }
}
console.log(`\n${tests.length - failures}/${tests.length} tests réussis`);
process.exit(failures ? 1 : 0);
