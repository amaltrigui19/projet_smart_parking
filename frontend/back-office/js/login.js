// Login of the back office (contract §2.5). Only OPERATOR accounts are accepted; there is no registration here
// (operator accounts are created by the administrator).
import { currentUser, errorMessage, goToHome, login } from '/shared/js/session.js';

// Already logged in as an operator: go straight to the dashboard.
if (currentUser()) goToHome();

const form = document.getElementById('login-form');
const message = document.getElementById('auth-message');
const VALID_EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function show(kind, text) {
  message.hidden = !text;
  message.className = `message ${kind}`;
  message.setAttribute('role', kind === 'error' ? 'alert' : 'status');
  message.textContent = text;
}

function setBusy(busy) {
  for (const field of form.elements) field.disabled = busy;
}

form.addEventListener('submit', async (evt) => {
  evt.preventDefault();
  const email = form.elements.email.value.trim().toLowerCase();
  const password = form.elements.password.value;
  if (!VALID_EMAIL.test(email) || !password) {
    show('error', 'Saisissez votre email et votre mot de passe.');
    return;
  }
  setBusy(true);
  show('info', 'Connexion…');
  const r = await login(email, password);
  if (r.status === 200) {
    goToHome();
    return;
  }
  setBusy(false);
  show('error', errorMessage(r, { 401: 'Email ou mot de passe incorrect.' }));
  form.elements.password.select();
});

if (new URLSearchParams(location.search).has('expired')) {
  show('info', 'Votre session a expiré : reconnectez-vous.');
}
