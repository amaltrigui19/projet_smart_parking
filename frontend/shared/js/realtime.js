// Real-time state of the spots (contract docs/contrats.md §1.4 and §2.6), independent of how the map is drawn.
//
//   1. opens the WebSocket /api/ws/spots?token=<jwt>;
//   2. once it is open, loads the full state (GET /api/spots), then applies the messages
//      { spot, state, since } (those received during the load are kept, then applied);
//   3. if the WebSocket closes: automatic reconnection (1 s, 2 s, 5 s, then 10 s at most)
//      and a new load of the full state on every reconnection;
//   4. missing, expired or rejected token (401): session cleared and back to the login page (§1.2).
//
// Use with the SVG map (convention: each spot carries data-spot="A3"):
//   import { connectMap, showStatus } from './realtime.js';
//   connectMap(document.querySelector('svg'), { onStatus: showStatus(badge) });
// or, without touching the DOM:
//   startRealtime({ onChange(spot, state, info) { ... } });
import { callApi, clearSession, goToLogin, readToken } from './session.js';

export const STATES = ['free', 'occupied', 'reserved']; // = the CSS classes of the spots
export const RECONNECT_DELAYS_MS = [1000, 2000, 5000, 10000];
// WebSocket closed by the server for a rejected token (proposal to the server: 4401; 1008 = "policy violation").
export const TOKEN_REJECTED_CODES = [4401, 1008];
export const STATUS = {
  CONNECTING: 'connecting',
  CONNECTED: 'connected',
  RECONNECTING: 'reconnecting',
  OFFLINE: 'offline',
};

const SPOT_ID = /^[A-Za-z0-9_-]+$/;

// wss:// over HTTPS (VM, local server), ws:// on http://localhost (fake API).
export function webSocketUrl(token, address = globalThis.location) {
  const protocol = address.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${address.host}/api/ws/spots?token=${encodeURIComponent(token)}`;
}

const instant = (iso) => {
  const t = Date.parse(iso);
  return Number.isNaN(t) ? null : t;
};

export function startRealtime(options = {}) {
  const {
    onChange = () => {}, // (spot, state, info): for each spot, on the full state then on each message
    onFullState = () => {}, // (spots: Map) after each load of the full state
    onStatus = () => {}, // (status, detail): see STATUS
    onOtherMessage = () => {}, // messages with a "type" field (events, Pi status: proposal §2.6)
    // Replaceable dependencies (automated tests)
    WebSocket: WebSocketClass = globalThis.WebSocket,
    loadSpots = () => callApi('/api/spots'),
    readToken: getToken = readToken,
    url = (token) => webSocketUrl(token),
    onSessionExpired = () => { clearSession(); goToLogin(true); },
    isOnline = () => (typeof navigator === 'undefined' ? true : navigator.onLine),
    timer = { set: (f, ms) => setTimeout(f, ms), clear: (id) => clearTimeout(id) },
    delays = RECONNECT_DELAYS_MS,
  } = options;

  const spots = new Map(); // id -> { id, state, since, type, max_duration_min }
  let socket = null; // current WebSocket (events of older ones are ignored)
  let pending = null; // messages received before the end of the full-state load
  let failures = 0; // failed attempts since the last successful connection
  let timerId = null;
  let stopped = false;
  let status = null;

  function setStatus(next, detail = {}) {
    status = next;
    onStatus(next, detail);
  }

  // One message { spot, state, since }. Ignored if malformed or older than the known state.
  function apply(message) {
    if (!message || typeof message.spot !== 'string' || !SPOT_ID.test(message.spot)) return false;
    if (!STATES.includes(message.state)) return false;
    const current = spots.get(message.spot);
    const t = instant(message.since);
    const tCurrent = current ? instant(current.since) : null;
    if (t !== null && tCurrent !== null && t < tCurrent) return false;
    const info = { ...current, id: message.spot, state: message.state, since: message.since ?? current?.since };
    spots.set(message.spot, info);
    onChange(message.spot, message.state, info);
    return true;
  }

  function handle(message) {
    if (message && typeof message === 'object' && 'type' in message) onOtherMessage(message);
    else apply(message);
  }

  // Response of GET /api/spots: it is the reference, it replaces the known state.
  function applyFullState(body) {
    for (const s of body.spots) {
      if (!s || typeof s.id !== 'string' || !SPOT_ID.test(s.id) || !STATES.includes(s.state)) continue;
      const info = { ...s };
      spots.set(s.id, info);
      onChange(s.id, s.state, info);
    }
    onFullState(spots);
  }

  const validResponse = (r) => r && r.status === 200 && r.body && Array.isArray(r.body.spots);

  function endSession() {
    stop();
    setStatus(STATUS.OFFLINE, { reason: 'session' });
    onSessionExpired();
  }

  function open() {
    timerId = null;
    if (stopped || socket) return;
    const token = getToken();
    if (!token) return endSession();
    if (status === null) setStatus(STATUS.CONNECTING);

    let ws;
    try {
      ws = new WebSocketClass(url(token));
    } catch {
      return afterClose(1006, false);
    }
    socket = ws;
    pending = [];
    let opened = false;

    ws.onopen = async () => {
      if (ws !== socket) return;
      opened = true;
      const r = await loadSpots();
      if (ws !== socket || stopped) return;
      if (validResponse(r)) {
        applyFullState(r.body);
        const messages = pending;
        pending = null;
        messages.forEach(handle);
        failures = 0;
        setStatus(STATUS.CONNECTED);
      } else if (r && r.status === 401) {
        endSession();
      } else {
        ws.close(); // full state unavailable: try again later
      }
    };
    ws.onmessage = (event) => {
      if (ws !== socket) return;
      let message;
      try { message = JSON.parse(event.data); } catch { return; }
      if (pending) pending.push(message);
      else handle(message);
    };
    ws.onerror = () => {}; // always followed by onclose
    ws.onclose = (event) => {
      if (ws !== socket) return;
      socket = null;
      pending = null;
      afterClose(event?.code, opened);
    };
  }

  async function afterClose(code, wasOpen) {
    if (stopped) return;
    if (TOKEN_REJECTED_CODES.includes(code)) return endSession();
    let unreachable = !isOnline();
    // Connection refused before opening: a browser does not give the HTTP code of the refusal.
    // The REST API does: 401 = token rejected, 0 = server unreachable. If it answers, the map stays up to date.
    if (!wasOpen && !unreachable) {
      const r = await loadSpots();
      if (stopped || socket) return; // stopped, or already reconnected meanwhile (network back)
      if (r && r.status === 401) return endSession();
      if (validResponse(r)) applyFullState(r.body);
      unreachable = !r || r.status === 0;
    }
    const delay = delays[Math.min(failures, delays.length - 1)];
    failures += 1;
    setStatus(unreachable ? STATUS.OFFLINE : STATUS.RECONNECTING, { delay, attempt: failures });
    timerId = timer.set(open, delay);
  }

  // New attempt right now (for example when the network comes back).
  function reconnectNow() {
    if (stopped || socket) return;
    if (timerId !== null) timer.clear(timerId);
    open();
  }

  function stop() {
    stopped = true;
    if (timerId !== null) timer.clear(timerId);
    timerId = null;
    const ws = socket;
    socket = null;
    if (ws) ws.close(1000);
    if (typeof window !== 'undefined') window.removeEventListener('online', reconnectNow);
  }

  if (typeof window !== 'undefined') window.addEventListener('online', reconnectNow);
  open();

  return {
    spots,
    stop,
    reconnectNow,
    get status() { return status; },
  };
}

// ---------- Helpers for the SVG map ----------

// Puts the class free / occupied / reserved on the element [data-spot="<spot>"] of the map.
export function colorSpot(root, spot, state) {
  if (!SPOT_ID.test(spot) || !STATES.includes(state)) return false;
  const element = root.querySelector(`[data-spot="${spot}"]`);
  if (!element) return false;
  element.classList.remove(...STATES);
  element.classList.add(state);
  element.setAttribute('data-state', state);
  return true;
}

// Connects an SVG map (or any element containing [data-spot] elements) to the real-time state.
export function connectMap(root, options = {}) {
  return startRealtime({
    ...options,
    onChange(spot, state, info) {
      colorSpot(root, spot, state);
      options.onChange?.(spot, state, info);
    },
  });
}

// Returns an onStatus function that shows the connection status in an element (class status-<status>).
export function showStatus(element) {
  return (status, detail = {}) => {
    const texts = {
      [STATUS.CONNECTING]: 'Connexion…',
      [STATUS.CONNECTED]: 'Temps réel : connecté',
      [STATUS.RECONNECTING]: `Reconnexion dans ${Math.round((detail.delay || 0) / 1000)} s…`,
      [STATUS.OFFLINE]: detail.reason === 'session'
        ? 'Session expirée'
        : `Hors ligne · nouvel essai dans ${Math.round((detail.delay || 0) / 1000)} s`,
    };
    element.textContent = texts[status] || status;
    for (const s of Object.values(STATUS)) element.classList.remove(`status-${s}`);
    element.classList.add(`status-${status}`);
  };
}
