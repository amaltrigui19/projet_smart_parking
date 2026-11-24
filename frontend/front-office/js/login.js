// Login and registration of the front office (contract §2.5). Only DRIVER accounts are accepted here;
// operators are sent to the back office (/admin/).
import { currentUser, errorMessage, goToHome, login, register } from '/shared/js/session.js';

// Already logged in: go straight to the driver space.
if (currentUser()) goToHome();

const el = (id) => document.getElementById(id);
const loginForm = el('login-form');
const registerForm = el('register-form');
const loginTab = el('login-tab');
const registerTab = el('register-tab');
const message = el('auth-message');

const VALID_EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function show(kind, text) {
  message.hidden = !text;
  message.className = `message ${kind}`;
  message.setAttribute('role', kind === 'error' ? 'alert' : 'status');
  message.textContent = text;
}

function selectTab(registering) {
  loginTab.setAttribute('aria-selected', String(!registering));
  registerTab.setAttribute('aria-selected', String(registering));
  loginForm.hidden = registering;
  registerForm.hidden = !registering;
  show('info', '');
  (registering ? registerForm : loginForm).elements[0].focus();
}
loginTab.addEventListener('click', () => selectTab(false));
registerTab.addEventListener('click', () => selectTab(true));

function setBusy(form, busy) {
  for (const field of form.elements) field.disabled = busy;
}

async function signIn(email, password) {
  const r = await login(email, password);
  if (r.status === 200) {
    goToHome();
    return true;
  }
  show('error', errorMessage(r, { 401: 'Email ou mot de passe incorrect.' }));
  return false;
}

loginForm.addEventListener('submit', async (evt) => {
  evt.preventDefault();
  const email = loginForm.elements.email.value.trim().toLowerCase();
  const password = loginForm.elements.password.value;
  if (!VALID_EMAIL.test(email) || !password) {
    show('error', 'Saisissez votre email et votre mot de passe.');
    return;
  }
  setBusy(loginForm, true);
  show('info', 'Connexion…');
  const ok = await signIn(email, password);
  setBusy(loginForm, false);
  if (!ok) loginForm.elements.password.select();
});

registerForm.addEventListener('submit', async (evt) => {
  evt.preventDefault();
  const f = registerForm.elements;
  const name = f.name.value.trim();
  const email = f.email.value.trim().toLowerCase();
  const password = f.password.value;
  // Same rules as the server (RegistrationValidator); the server still decides.
  let error = '';
  if (!name) error = 'Le nom est obligatoire.';
  else if (!VALID_EMAIL.test(email)) error = "L'adresse email n'est pas valide.";
  else if (password.length < 8 || password.length > 128) error = 'Le mot de passe doit faire entre 8 et 128 caractères.';
  else if (password !== f.confirmation.value) error = 'Les deux mots de passe ne sont pas identiques.';
  if (error) { show('error', error); return; }

  setBusy(registerForm, true);
  show('info', 'Création du compte…');
  const r = await register(name, email, password);
  if (r.status === 201) {
    // Registration does not return a token: log in right after.
    show('success', 'Compte créé. Connexion…');
    const ok = await signIn(email, password);
    if (!ok) {
      setBusy(registerForm, false);
      selectTab(false);
      loginForm.elements.email.value = email;
      show('success', 'Compte créé. Connectez-vous avec votre email et votre mot de passe.');
    }
    return;
  }
  setBusy(registerForm, false);
  show('error', errorMessage(r, { 409: 'Un compte existe déjà avec cet email. Connectez-vous.' }));
});

// Arriving here after a 401 on another page: a word of explanation.
if (new URLSearchParams(location.search).has('expired')) {
  show('info', 'Votre session a expiré : reconnectez-vous.');
}
