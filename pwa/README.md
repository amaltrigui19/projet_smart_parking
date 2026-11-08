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
## Calibration des places (`calibration.html`)

Page de l'exploitant pour indiquer au Pi où se trouvent les places sur l'image de la caméra. On dessine chaque place et chaque zone interdite sur la photo de référence, et la page produit `places.json` au format du contrat des polygones (`docs/contrats.md` §5.2).

À refaire à chaque fois que la caméra ou la maquette bouge.

### Utilisation
1. Ouvrir `pwa/calibration.html` dans Chrome (double-clic, aucun serveur nécessaire).
2. Cliquer sur **Choisir la photo** et charger la photo de référence prise par le Pi, en 640 × 480. Une autre résolution affiche un avertissement, car les polygones seraient décalés.
3. Pour chaque place : cliquer sur ses 4 coins en faisant le tour, puis **Fermer le polygone**. Le formulaire propose le nom et le type suivants selon le plan (A1 pmr… B4 electrique) ; vérifier, puis **Enregistrer**.
4. Pour une zone interdite : même chose, en cochant « une zone interdite » (nom proposé : `allee1`).
5. Cliquer sur **Exporter places.json**.

Raccourcis pendant le tracé : Entrée ferme le polygone, Retour arrière annule le dernier point, Échap efface le tracé.

### Vérifier le fichier
```bash
python pwa/outils/valider_places.py chemin/vers/places.json
```
Attendu : `[OK] ... Fichier conforme au contrat.` En cas d'erreur, le script indique la place et le problème.

### Limites actuelles
- Le fichier est téléchargé ; l'envoi direct à l'API est prévu au sprint 3.
- On ne peut pas déplacer un coin : supprimer la place dans la liste et la redessiner.
- Ne pas versionner `places.json` ni les photos de test.