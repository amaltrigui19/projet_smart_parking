// Home page of the front office: if the driver is already logged in, the button opens their space.
import { currentApp, currentUser } from '/shared/js/session.js';

const link = document.getElementById('app-link');
if (link && currentUser()) {
  link.href = currentApp().homePage;
  link.textContent = 'Ouvrir mon espace conducteur';
}
