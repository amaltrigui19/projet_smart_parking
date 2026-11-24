// Protected pages of both applications: role check, user name, logout button.
// The page stays hidden (<body hidden>) until the session is checked. Real protection is on the server
// (@RolesAllowed on every route).
import { callApi, logout, requireUser } from './session.js';

const user = requireUser();

if (user) {
  const name = document.getElementById('user-name');
  if (name) name.textContent = user.name || user.email || '';
  document.getElementById('logout-button')?.addEventListener('click', logout);
  document.body.hidden = false;

  // The server confirms the token is still valid (a 401 sends back to the login page).
  callApi('/api/auth/me').then((r) => {
    if (r.status === 200 && r.body && name) name.textContent = r.body.name || r.body.email;
  });
}
