// Live map page (front office and back office): draws the PROVISIONAL parking map and connects it to
// shared/js/realtime.js. The drawing will be replaced by the one built from GET /api/parking (contract §4.3);
// what stays is realtime.js and the data-spot="A1" convention with the classes free / occupied / reserved.
import { currentUser } from './session.js';
import { connectMap, showStatus, STATUS } from './realtime.js';

const LABELS = { free: 'libre', occupied: 'occupée', reserved: 'réservée' };
const LOG_SIZE = 8;
const SVG_NS = 'http://www.w3.org/2000/svg';

// Provisional plan, to the scale of contract §4.1 (1 unit = 1 cm, 60 × 40 cm model).
// [id, x, y, type label]
const SPOTS = [
  ['A1', 14, 0, 'PMR'], ['A2', 22, 0], ['A3', 30, 0], ['A4', 38, 0],
  ['B1', 14, 27], ['B2', 22, 27], ['B3', 30, 27], ['B4', 38, 27, 'ÉLEC'],
];

function svgElement(name, attributes = {}, text = '') {
  const e = document.createElementNS(SVG_NS, name);
  for (const [k, v] of Object.entries(attributes)) e.setAttribute(k, v);
  if (text) e.textContent = text;
  return e;
}

function drawMap(container) {
  const svg = svgElement('svg', { viewBox: '0 0 60 40', role: 'img', 'aria-labelledby': 'map-title' });
  svg.append(
    svgElement('title', { id: 'map-title' }, 'Plan du parking P1 : 8 places, A1 à A4 en haut, B1 à B4 en bas'),
    svgElement('rect', { class: 'ground', x: 0, y: 0, width: 60, height: 40, rx: 1 }),
    svgElement('rect', { class: 'lane', x: 0, y: 13, width: 60, height: 14 }),
    svgElement('rect', { class: 'forbidden', x: 50, y: 13, width: 10, height: 14 }),
    svgElement('text', { class: 'landmark', x: 5.5, y: 20 }, 'ENTRÉE'),
    svgElement('text', { class: 'landmark', x: 55, y: 20 }, 'SORTIE'),
  );
  for (const [id, x, y, typeLabel] of SPOTS) {
    const g = svgElement('g', { 'data-spot': id });
    // Number centred in the spot, or higher up when a type label is written under it
    const numberY = y === 0 || typeLabel ? y + 5.5 : y + 7.5;
    g.append(
      svgElement('title', {}, typeLabel ? `${id} (${typeLabel === 'PMR' ? 'PMR' : 'électrique'})` : id),
      svgElement('rect', { x, y, width: 8, height: 13 }),
      svgElement('text', { class: 'spot-number', x: x + 4, y: numberY }, id),
    );
    if (typeLabel) g.append(svgElement('text', { class: 'spot-type', x: x + 4, y: y + 10 }, typeLabel));
    svg.append(g);
  }
  container.append(svg);
  return svg;
}

// Without a valid session, protected-page.js already sends back to the login page: start nothing.
if (currentUser()) {
  const container = document.getElementById('parking-map');
  const map = drawMap(container);
  const log = document.getElementById('change-log');
  const shown = new Map(); // spot -> state shown (to log only real changes)
  let fullStateLoaded = false;

  const time = (iso) => {
    const d = iso ? new Date(iso) : new Date();
    return Number.isNaN(d.getTime()) ? '' : d.toLocaleTimeString('fr-FR');
  };

  function addToLog(text) {
    const line = document.createElement('li');
    line.textContent = text;
    log.prepend(line);
    while (log.children.length > LOG_SIZE) log.lastElementChild.remove();
  }

  function updateSummary(spots) {
    const n = { free: 0, occupied: 0, reserved: 0 };
    for (const s of spots.values()) n[s.state] += 1;
    document.getElementById('count-free').textContent = n.free;
    document.getElementById('count-occupied').textContent = n.occupied;
    document.getElementById('count-reserved').textContent = n.reserved;
  }

  const badge = showStatus(document.getElementById('realtime-status'));
  const realtime = connectMap(map, {
    onStatus(status, detail) {
      badge(status, detail);
      // Map greyed out while it may be out of date (WebSocket closed)
      container.classList.toggle('stale', status !== STATUS.CONNECTED);
    },
    onChange(spot, state, info) {
      // Spot tooltip: "A1 (PMR) : libre"
      const title = map.querySelector(`[data-spot="${spot}"] > title`);
      if (title) title.textContent = `${title.textContent.split(' : ')[0]} : ${LABELS[state]}`;
      const before = shown.get(spot);
      shown.set(spot, state);
      if (fullStateLoaded) {
        if (before !== state) addToLog(`${time(info.since)} · ${spot} : ${LABELS[state]}`);
        updateSummary(realtime.spots);
      }
    },
    onFullState(spots) {
      fullStateLoaded = true;
      updateSummary(spots);
      addToLog(`${time()} · état complet chargé (${spots.size} places)`);
    },
  });
}
