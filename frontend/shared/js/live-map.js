// Live map page (front office and back office): draws the parking map from GET /api/parking
// (shared/js/parking-plan.js, contract §4.3) and connects it to shared/js/realtime.js.
// If the plan cannot be loaded (fake API, server without plan), the plan of the scale model is used.
import { callApi, currentUser } from './session.js';
import { connectMap, showStatus, STATUS } from './realtime.js';
import { DEFAULT_LAYOUT, drawParkingPlan, isDrawable } from './parking-plan.js';

const LABELS = { free: 'libre', occupied: 'occupée', reserved: 'réservée' };
const LOG_SIZE = 8;

async function loadLayout() {
  try {
    const response = await callApi('/api/parking');
    if (response.status === 200 && isDrawable(response.body)) return response.body;
    console.warn(`GET /api/parking : ${response.status}, plan de la maquette utilisé`);
  } catch (error) {
    console.warn('GET /api/parking impossible, plan de la maquette utilisé', error);
  }
  return DEFAULT_LAYOUT;
}

// Without a valid session, protected-page.js already sends back to the login page: start nothing.
if (currentUser()) {
  const container = document.getElementById('parking-map');
  const map = drawParkingPlan(container, await loadLayout());
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
