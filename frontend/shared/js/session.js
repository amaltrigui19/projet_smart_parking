// User session: JWT, role, API calls (contract docs/contrats.md §1.2 and §2.5).
// Shared by the front office (/) and the back office (/admin/). Each page says which application it belongs to
// with <html data-app="front-office"> or <html data-app="back-office">.
// No security here: the server checks the role on every call.

// Each application accepts ONE role and keeps its own session (separate sessionStorage keys), so a driver
// session never opens the back office and an operator session never opens the front office.
export const APPS = {
  'front-office': {
    role: 'DRIVER',
    storagePrefix: 'sp.driver.',
    loginPage: '/login.html',
    homePage: '/dashboard.html',
    wrongRoleMessage: 'Ce compte est un compte opérateur : connectez-vous sur le back office (/admin/).',
  },
  'back-office': {
    role: 'OPERATOR',
    storagePrefix: 'sp.operator.',
    loginPage: '/admin/',
    homePage: '/admin/dashboard.html',
    wrongRoleMessage: "Ce compte n'a pas accès au back office : il est réservé aux opérateurs.",
  },
};

const appName = () =>
  (typeof document !== 'undefined' && document.documentElement.dataset.app) || 'front-office';
export const currentApp = () => APPS[appName()] || APPS['front-office'];

// Contract §1.2: the token is kept in sessionStorage (never in localStorage, never in a URL).
const key = (name) => currentApp().storagePrefix + name;
const storage = () => window.sessionStorage;

function read(name) {
  try { return storage().getItem(key(name)); } catch { return null; }
}

// JWT payload (signature NOT checked: only the server does that). null if unreadable.
export function tokenPayload(token) {
  const parts = String(token).split('.');
  if (parts.length !== 3) return null;
  try {
    const b64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const bytes = Uint8Array.from(atob(b64 + '='.repeat((4 - (b64.length % 4)) % 4)), (c) => c.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(bytes)); // UTF-8: names with accents
  } catch {
    return null;
  }
}

// The token if present and not expired, otherwise null (and the session is cleared).
export function readToken() {
  const token = read('token');
  if (!token) return null;
  const expiresAt = Number(read('expires_at')) || (tokenPayload(token)?.exp ?? 0) * 1000;
  if (expiresAt && Date.now() >= expiresAt) {
    clearSession();
    return null;
  }
  return token;
}

// The logged-in user ({ id, name, email, role }) if their role matches this application, otherwise null.
export function currentUser() {
  const token = readToken();
  if (!token) return null;
  let user = null;
  try { user = JSON.parse(read('user')); } catch { /* unreadable */ }
  // Role returned by the login (user.role); otherwise the "role" claim of the JWT (same value).
  const role = user?.role ?? tokenPayload(token)?.role;
  if (role !== currentApp().role) return null;
  return { ...(user || {}), role };
}

export function clearSession() {
  try {
    for (const name of ['token', 'user', 'expires_at']) storage().removeItem(key(name));
  } catch { /* storage unavailable: nothing to clear */ }
}

function openSession({ access_token, expires_in, user }) {
  storage().setItem(key('token'), access_token);
  storage().setItem(key('user'), JSON.stringify(user));
  if (expires_in) storage().setItem(key('expires_at'), String(Date.now() + expires_in * 1000));
}

// API call. Returns { status, body }; status 0 = server unreachable.
// withToken: false for register and login. The server rejects any invalid token with 401, even on a public
// route: we therefore never send an Authorization header on these two routes.
export async function callApi(path, { method = 'GET', body, withToken = true } = {}) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (withToken) {
    const token = readToken();
    if (token) headers.Authorization = `Bearer ${token}`;
  }
  let response;
  try {
    response = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      cache: 'no-store',
    });
  } catch {
    return { status: 0, body: null };
  }
  let json = null;
  try { json = await response.json(); } catch { /* response without JSON */ }
  // Contract §1.2: on a 401 from a protected route, clear the token and go back to the login page.
  if (response.status === 401 && withToken) {
    clearSession();
    goToLogin(true);
  }
  return { status: response.status, body: json };
}

// Message to show for an error: the server's "message" field (contract §2.1) when there is one.
export function errorMessage({ status, body }, byStatus = {}) {
  if (status === 0) return 'Serveur injoignable : vérifiez votre connexion, puis réessayez.';
  if (byStatus[status]) return byStatus[status];
  if (body && body.message) return body.message;
  return `Erreur inattendue du serveur (${status}).`;
}

// POST /api/auth/login. A valid account with the role of the OTHER application is refused here:
// { status: 403, body: { error: 'wrong_application', message } }, and no session is opened.
export async function login(email, password) {
  const r = await callApi('/api/auth/login', { method: 'POST', body: { email, password }, withToken: false });
  if (r.status !== 200 || !r.body || !r.body.access_token || !r.body.user) return r;
  if (r.body.user.role !== currentApp().role) {
    return { status: 403, body: { error: 'wrong_application', message: currentApp().wrongRoleMessage } };
  }
  openSession(r.body);
  return r;
}

export function register(name, email, password) {
  return callApi('/api/auth/register', { method: 'POST', body: { name, email, password }, withToken: false });
}

// sessionExpired: the login page says "your session has expired" (nothing sensitive in the URL).
export function goToLogin(sessionExpired = false) {
  location.replace(currentApp().loginPage + (sessionExpired ? '?expired' : ''));
}

export function goToHome() {
  location.replace(currentApp().homePage);
}

export function logout() {
  clearSession();
  goToLogin();
}

// To call at the top of every protected page: returns the user, or redirects to the login page.
export function requireUser() {
  const user = currentUser();
  if (!user) {
    clearSession();
    goToLogin();
    return null;
  }
  return user;
}
