# PWA Smart Parking

Application web progressive unique (HTML, CSS, JavaScript, modules ES, sans build). Elle accueillera deux interfaces selon le rôle du JWT : **conducteur** (front-office) et **exploitant** (back-office).

## État (sprint 1)

Base installable : coquille, manifeste, service worker, page hors ligne. Les interfaces conducteur et exploitant arrivent dans les sprints suivants.

## Structure

```
pwa/
├── index.html            # coquille et page d'accueil
├── offline.html          # page de repli hors ligne
├── manifest.json         # nom, icônes, couleurs, display: standalone
├── service-worker.js     # cache de la coquille (CACHE_VERSION à incrémenter)
├── css/style.css
├── js/app.js             # enregistrement du service worker, installation, état du réseau
└── icons/                # icônes 192, 512, maskable + generer_icones.py
```

## Lancer en local

```bash
cd pwa
python -m http.server 8080
```

Ouvrir `http://localhost:8080`. Un service worker n'est actif qu'en HTTPS ou sur `localhost`.

## Tester

1. DevTools > Application : le manifeste ne montre aucune erreur, le service worker est « activated ».
2. DevTools > Network > Offline, recharger : la page d'accueil s'affiche toujours.
3. Lighthouse (PWA / installable) : manifeste et service worker valides ; le bouton « Installer l'application » apparaît dans les navigateurs qui le permettent.

## Modifier la coquille

Si vous ajoutez ou changez un fichier de la coquille, ajoutez-le à `COQUILLE` dans `service-worker.js` et **incrémentez `CACHE_VERSION`**, sinon les anciens fichiers restent servis depuis le cache.

Les appels à l'API (`/api/...`) ne sont jamais mis en cache.

## Régénérer les icônes

```bash
pip install pillow
python icons/generer_icones.py
```
