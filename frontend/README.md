# PWA Smart Parking

Application web progressive unique (HTML, CSS, JavaScript, modules ES, sans build). Elle accueillera deux interfaces selon le rôle du JWT : **conducteur** (front-office) et **exploitant** (back-office).

## État (sprint 4)

- Sprint 1 : base installable (coquille, manifeste, service worker, page hors ligne).
- Sprint 2-3 : outil de calibration des places (voir plus bas).
- Sprint 3 : **connexion et inscription**, jeton JWT, redirection selon le rôle, déconnexion (voir « Connexion »).
- Sprint 4 : **plan en temps réel** par WebSocket, testé avec un faux serveur (voir « Plan en temps réel »).

## Structure

```
frontend/
├── index.html            # coquille et page d'accueil
├── connexion.html        # connexion et inscription
├── conducteur.html       # front-office (rôle CONDUCTEUR)
├── exploitant.html       # back-office (rôle EXPLOITANT)
├── offline.html          # page de repli hors ligne
├── manifest.json         # nom, icônes, couleurs, display: standalone
├── service-worker.js     # cache de la coquille (CACHE_VERSION à incrémenter)
├── css/style.css
├── js/app.js             # enregistrement du service worker, installation, état du réseau
├── js/auth.js            # session : jeton JWT, rôle, appels à l'API, déconnexion
├── js/connexion.js       # formulaires de connexion et d'inscription
├── js/espace.js          # pages réservées : contrôle du rôle, nom, déconnexion
├── js/accueil.js         # accueil : lien vers son espace si déjà connecté
├── js/temps-reel.js      # état des places en temps réel (GET /api/places + WebSocket), sans dessin
├── plan-temps-reel.html  # démo provisoire du plan en temps réel (+ js/plan-temps-reel.js, css/plan.css)
└── icons/                # icônes 192, 512, maskable + generer_icones.py
```

## Lancer en local

```bash
cd frontend
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
## Connexion (`connexion.html`)

Contrat : `docs/contrats.md` §1.2 et §2.5. Les routes serveur sont celles de `middleware/` (`/api/auth/...`).

- **Connexion** : `POST /api/auth/connexion`. En cas de succès, le jeton est rangé dans **`sessionStorage`, clé `jwt`** (lu aussi par la calibration), avec l'utilisateur et l'heure d'expiration.
- **Inscription** : `POST /api/auth/inscription` (toujours un conducteur), puis connexion automatique.
- **Redirection selon le rôle** (`utilisateur.role` de la réponse de connexion) : `CONDUCTEUR` → `conducteur.html`, `EXPLOITANT` → `exploitant.html`. Un conducteur qui ouvre `exploitant.html` est renvoyé vers son espace. La vraie protection reste le serveur.
- **Déconnexion** : bouton « Se déconnecter », qui efface la session.
- **Session expirée** : sur un `401` d'une route protégée (`appelerApi` de `js/auth.js`), la session est effacée et on revient à `connexion.html?expire`.
- Aucun en-tête `Authorization` n'est envoyé sur l'inscription et la connexion : le serveur refuse en `401` tout jeton invalide, même sur une route publique.
- Erreurs : le champ `message` du serveur est affiché (« Email ou mot de passe incorrect. » pour un `401`).

Pour une nouvelle page réservée : `<body data-role="EXPLOITANT" hidden>` et `<script type="module" src="js/espace.js">`. Les appels à l'API passent par `appelerApi()` de `js/auth.js`.

### Tester avec la fausse API

`outils/fausse_api.py` implémente aussi `POST /api/auth/inscription`, `POST /api/auth/connexion` et `GET /api/auth/moi`. Les comptes sont en mémoire et deux comptes de test existent au démarrage :

| Email | Mot de passe | Rôle |
|---|---|---|
| `exploitant@exemple.tn` | `exploitant123` | EXPLOITANT |
| `conducteur@exemple.tn` | `conducteur123` | CONDUCTEUR |

Le jeton renvoyé a la forme d'un JWT mais **n'est pas signé** : outil de test uniquement.

```bash
python frontend/outils/fausse_api.py     # puis ouvrir http://localhost:8000/connexion.html
```

1. Mauvais mot de passe → « Email ou mot de passe incorrect. ».
2. Compte exploitant → `exploitant.html`. La carte « Calibration des places » ouvre la calibration, déjà connectée.
3. Ouvrir `conducteur.html` en étant exploitant → retour vers `exploitant.html`.
4. « Se déconnecter » → `connexion.html`, `sessionStorage` vide.
5. « Créer un compte » → `conducteur.html`. Le même email une seconde fois → « Un compte existe déjà… ».
6. Console : `sessionStorage.setItem("jwt", "bidon")`, puis ouvrir `conducteur.html` → retour vers la connexion avec « Votre session a expiré ».

Avec le vrai serveur (PR serveur JWT) : même parcours sur https://localhost/connexion.html, avec le compte de `EXPLOITANT_EMAIL` de `server/.env`.

## Calibration des places (`calibration.html`)

Page de l'exploitant pour indiquer au Pi où se trouvent les places sur l'image de la caméra. On dessine chaque place et chaque zone interdite sur la photo de référence. La page **enregistre** le fichier `places.json` sur le serveur (`PUT /api/config/polygones`, contrat §2.10), au format du contrat des polygones (§5.2). Elle peut aussi l'exporter.

À refaire à chaque fois que la caméra ou la maquette bouge.

| Fichier | Rôle |
|---|---|
| `calibration.html`, `css/calibration.css` | Page et mise en forme |
| `js/calibration.js` | Dessin des polygones, chargement (`GET`) et enregistrement (`PUT`) sur le serveur |
| `js/validation-places.js` | Validation avant l'envoi, avec les mêmes règles que le serveur (erreur `400 config_invalide`) |
| `outils/fausse_api.py` | Fausse API locale pour tester sans le vrai serveur |
| `outils/relire_polygones.py` | Relit les polygones enregistrés et affiche un résumé (démo) |
| `outils/valider_places.py` | Vérifie un fichier `places.json` sur le disque |
| `outils/test_validation.js`, `outils/test_fausse_api.py` | Tests automatiques |

### Utilisation

1. Ouvrir la page **par le serveur** (voir plus bas). Au chargement, elle récupère les polygones déjà enregistrés (`GET /api/config/polygones`). S'il n'y en a pas, on part d'un plan vide.
2. Cliquer sur **Choisir la photo** et charger la photo de référence prise par le Pi, en 640 × 480. Une autre résolution affiche un avertissement, car les polygones seraient décalés.
3. Pour chaque place : cliquer sur ses 4 coins en faisant le tour, puis **Fermer le polygone**. Le formulaire propose le nom et le type suivants selon le plan (A1 pmr … B4 electrique). Vérifier, puis **Enregistrer**. Seules les places du plan (A1 à B4) sont acceptées, comme sur le serveur.
4. Pour une zone interdite : même chose, en cochant « une zone interdite » (nom proposé : `allee1`).
5. **Modifier** (dans la liste) : change le nom, le type ou la durée, ou permet de retoucher le tracé avec « Modifier le tracé ». **Annuler** remet l'élément tel qu'il était.
6. Cliquer sur **Enregistrer sur le serveur**. La page vérifie d'abord le fichier, avec les mêmes règles que le serveur, puis l'envoie en entier. Le message indique le succès, ou la liste des problèmes.

Raccourcis pendant le tracé : Entrée ferme le polygone, Retour arrière annule le dernier point, Échap efface le tracé.

Ouverte par un double-clic (`file://`), la page sert seulement à dessiner et à exporter : l'enregistrement sur le serveur demande de passer par un serveur web.

### Jeton JWT

L'API exige un jeton d'**exploitant** : `Authorization: Bearer <jwt>` (contrat §1.2). La page le lit dans **`sessionStorage`, clé `jwt`**, remplie par la page de connexion (`connexion.html`, compte exploitant). L'emplacement est défini à un seul endroit, en haut de `js/calibration.js` (`STOCKAGE_JWT` et `CLE_JWT`).

On peut aussi placer un jeton à la main. Dans la console du navigateur (F12 → Console), sur la page de calibration :

```js
sessionStorage.setItem("jwt", "exploitant")   // jeton de test accepté par la fausse API
```

Réponses gérées :
- `401` : le jeton est effacé, puis la page demande de se reconnecter ;
- `403` : accès réservé à l'exploitant ;
- `400 config_invalide` : le détail renvoyé par le serveur est affiché ;
- serveur injoignable : message clair. Les polygones restent sur la page, et on peut toujours les exporter.

### Tester avec la fausse API (sans le vrai serveur)

`outils/fausse_api.py` sert la PWA **et** implémente `GET` et `PUT /api/config/polygones` selon le contrat. Elle n'utilise que la bibliothèque standard de Python et réutilise les règles de `valider_places.py`, avec en plus la règle « place absente du plan ». Le fichier enregistré va dans `frontend/outils/donnees/polygones.json`, ignoré par Git.

```bash
python frontend/outils/fausse_api.py          # depuis la racine du dépôt ; Ctrl+C pour arrêter
```

Attendu : `Fausse API prête : http://localhost:8000/calibration.html`. Ouvrir cette adresse. Le port 8000 se change avec `--port 8001`.

Jetons de test :

| `sessionStorage.jwt` | Réponse de la fausse API |
|---|---|
| `exploitant` (ou tout autre texte) | accepté |
| `conducteur`, ou un JWT dont `role` vaut `CONDUCTEUR` | `403 acces_refuse` |
| absent | `401 non_authentifie` |

Parcours de test dans le navigateur :
1. Ouvrir http://localhost:8000/calibration.html. Le message indique « Non connecté, ou session expirée (401) ».
2. Console : `sessionStorage.setItem("jwt", "exploitant")`, puis recharger. Le message devient « Aucun fichier enregistré sur le serveur : on part d'un plan vide ».
3. Charger une photo en 640 × 480, dessiner A1, A2 et une zone, puis **Enregistrer sur le serveur**. Attendu : « Enregistré sur le serveur à … : 2 place(s), 1 zone(s) interdite(s) ».
4. Recharger la page. Attendu : « 2 place(s) et 1 zone(s) chargées depuis le serveur » et la liste remplie. Recharger la photo pour les revoir dessinées.
5. Cas d'erreur `400` renvoyé par le serveur. La page vérifie avant l'envoi : pour voir la réponse du serveur, on contourne cette vérification dans la console :
   ```js
   window.validerPlaces = () => []                 // désactive la vérification locale
   etat.places.push({ ...etat.places[0] })         // ajoute une place en double
   ```
   Puis **Enregistrer sur le serveur**. Attendu : « Le serveur a refusé le fichier (config_invalide) : A1 : identifiant en double ». Recharger la page pour revenir à l'état normal.
6. Relire ce qui a été enregistré :
   ```bash
   python frontend/outils/relire_polygones.py
   ```
   Attendu : le tableau des places (type, durée), les zones, les places du plan pas encore dessinées, puis `[OK] fichier relu et conforme au contrat des polygones (§5.2).`

Tests automatiques :

```bash
node frontend/outils/test_validation.js                     # 13 tests de la validation côté client
python -m unittest frontend/outils/test_fausse_api.py -v    # 14 tests de la fausse API (polygones et authentification)
```

### Avec le vrai serveur (quand la route existera)

La page appelle `/api/config/polygones` sur **le même domaine** : il suffit de l'ouvrir via le serveur (Nginx sert `frontend/`).
- En local (`server/`, `docs/installation-serveur.md`) : https://localhost/calibration.html
- Sur la VM : https://smartparking-supcom.duckdns.org/calibration.html

Le jeton viendra de la page de connexion. Relire les polygones enregistrés :

```bash
python frontend/outils/relire_polygones.py --url https://localhost --cacert server/certs/ca.pem --jeton <jwt-exploitant>
python frontend/outils/relire_polygones.py --url https://smartparking-supcom.duckdns.org --jeton <jwt-exploitant>
```

(ou `SMARTPARKING_JWT=<jwt>` dans l'environnement, à la place de `--jeton`).

### Vérifier un fichier exporté

```bash
python frontend/outils/valider_places.py chemin/vers/places.json
```

Attendu : `[OK] ... Fichier conforme au contrat.` En cas d'erreur, le script indique la place et le problème.

### Limites actuelles

- La route serveur `GET` et `PUT /api/config/polygones` n'existe pas encore (qui la code est à décider en équipe) : on teste avec la fausse API.
- La page de connexion n'existe pas encore : on place le jeton à la main (voir « Jeton JWT »).
- Le plan (A1 à B4) est écrit dans `js/calibration.js` et dans les outils. Plus tard, il viendra de `GET /api/config/plan`.
- On ne peut pas déplacer un coin à la souris : **Modifier**, puis « Modifier le tracé » (retirer les derniers points et les replacer), ou redessiner.
- Ne pas versionner `places.json`, ni les photos de test.

## Plan en temps réel (sprint 4)

Contrat : `docs/contrats.md` §1.4 (WebSocket) et §2.6 (`GET /api/places`). Le plan change de couleur dès que le serveur envoie un changement, sans recharger la page.

| Fichier | Rôle |
|---|---|
| `js/temps-reel.js` | **Module réutilisable**, sans dessin : état complet, WebSocket, reconnexion, indicateur, 401 |
| `plan-temps-reel.html`, `js/plan-temps-reel.js`, `css/plan.css` | Page de démo avec un plan SVG **provisoire**, à remplacer par celui d'Amal |
| `outils/faux_websocket.py` | Faux serveur : la fausse API, plus `GET /api/places` et le WebSocket `/api/ws/places` |
| `outils/test_temps_reel.mjs`, `outils/test_faux_websocket.py` | Tests automatiques |

### Fonctionnement de `js/temps-reel.js`

1. Il ouvre `ws(s)://<hôte>/api/ws/places?token=<jwt>` (jeton lu dans `sessionStorage["jwt"]`, §1.2).
2. Dès que le WebSocket est ouvert, il charge l'**état complet** (`GET /api/places`), puis applique les messages `{ "place": "A3", "etat": "occupee", "depuis": "…Z" }`. Les messages reçus pendant ce chargement sont gardés, puis appliqués, sauf ceux plus anciens que l'état chargé : aucun changement n'est perdu.
3. Si le WebSocket se coupe, il se **reconnecte tout seul** après 1 s, 2 s, 5 s, puis toutes les 10 s, et **recharge l'état complet** à chaque reconnexion. Au retour du réseau (événement `online`), il réessaie tout de suite.
4. **Indicateur** : `connexion`, `connecte`, `reconnexion` (le serveur répond, mais pas le WebSocket) et `hors-ligne` (réseau coupé ou serveur injoignable). Sur la page de démo, le plan est grisé tant qu'il n'est pas à jour.
5. **401** : jeton absent ou expiré, `GET /api/places` en `401`, ou WebSocket fermé par le serveur avec le code `4401` (ou `1008`). Dans ces trois cas, la session est effacée et la page revient à `connexion.html?expire`. Un navigateur ne donne pas le code HTTP d'un WebSocket refusé : après un refus, le module interroge `GET /api/places` pour savoir si c'est un `401`.
6. Un message avec un champ `type` (événements et statut du Pi pour l'exploitant, proposition du §2.6) n'est pas un changement de place : il est transmis à `surAutreMessage`.

### Pour le plan SVG d'Amal : la convention

- Chaque place du SVG porte **`data-place="<id>"`** (`A1`…`B4`, comme dans l'API), sur la forme ou sur un `<g>` qui la contient.
- Le module met sur cet élément **une seule classe** parmi **`libre`**, **`occupee`** et **`reservee`** (sans accent, comme `etat` dans l'API), plus l'attribut `data-etat`.
- Les couleurs sont dans `css/plan.css` (`[data-place].libre { fill: … }`). Sur un `<g>`, `fill` passe aux formes qu'il contient : les textes (numéros) doivent avoir leur propre `fill`.

```js
import { brancherPlan, afficherStatut } from './temps-reel.js';

const tempsReel = brancherPlan(document.querySelector('#plan'), {
  surStatut: afficherStatut(document.querySelector('#statut-temps-reel')), // pastille de connexion
  surChangement(place, etat, info) { /* facultatif : infobulle, compteur… */ },
  surEtatComplet(places) { /* facultatif : Map id -> { id, etat, depuis, type, duree_max_min } */ },
});
// tempsReel.places (Map), tempsReel.statut, tempsReel.arreter()
```

Sans DOM : `demarrerTempsReel({ surChangement(place, etat) { … } })`.

### Tester avec le faux serveur

`outils/faux_websocket.py` reprend toute la fausse API (connexion, polygones) et ajoute `GET /api/places` et le WebSocket. Il envoie un changement aléatoire toutes les 2 secondes, à tous les clients connectés. Il n'utilise que la bibliothèque standard de Python (WebSocket minimal : texte, ping, fermeture) : **aucune dépendance à installer**.

| Jeton | `GET /api/places` et WebSocket |
|---|---|
| `exploitant`, `conducteur`, ou un jeton de la fausse connexion | accepté |
| absent, illisible ou expiré | `401`, poignée de main refusée |

```bash
python frontend/outils/faux_websocket.py                  # depuis la racine ; --intervalle 1, --port 8001
```

1. Ouvrir http://localhost:8000/connexion.html et se connecter (`conducteur@exemple.tn` / `conducteur123`, ou le compte exploitant).
2. Ouvrir http://localhost:8000/plan-temps-reel.html. Attendu : la pastille « Temps réel : connecté », puis une place qui change de couleur toutes les 2 s, le résumé et la liste « Derniers changements » mis à jour, **sans rechargement de la page**.
3. Arrêter le serveur (Ctrl+C). Attendu : « Reconnexion dans 1 s… », puis « Hors ligne · nouvel essai dans … s », et le plan grisé.
4. Relancer le serveur. Attendu : au plus 10 s plus tard, « Temps réel : connecté », « état complet chargé » dans la liste, et les changements qui reprennent, toujours sans recharger la page.
5. Console : `sessionStorage.setItem("jwt", "a.b.c")`, puis arrêter et relancer le serveur. Attendu : retour vers `connexion.html` avec « Votre session a expiré ».

DevTools > Network > WS : on voit la connexion `places?token=…` et les messages reçus.

Tests automatiques :

```bash
node frontend/outils/test_temps_reel.mjs                       # 15 tests du module (faux WebSocket, fausse minuterie)
python -m unittest frontend/outils/test_faux_websocket.py -v   # 6 tests du faux serveur (vrai WebSocket)
```

### Avec le vrai serveur

La page est servie par Nginx sur le même domaine que l'API : https://localhost/plan-temps-reel.html en local, et `wss://` est choisi automatiquement en HTTPS. Nginx transmet déjà l'en-tête `Upgrade` sur `/api/ws/` (`server/nginx/nginx.conf`).

### Limites actuelles

- Le plan de `plan-temps-reel.html` est **provisoire** : il sera remplacé par le plan SVG d'Amal (généré à partir de `GET /api/parking`). Le module, lui, reste.
- La page n'est pas encore reliée depuis `conducteur.html` et `exploitant.html` (pages d'Eya) : on l'ouvre par son adresse.
- Les fichiers de cette page ne sont pas dans la coquille hors ligne du service worker : le temps réel n'a pas de sens hors ligne.
