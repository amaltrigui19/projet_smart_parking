# Frontends Smart Parking

Deux applications web progressives **séparées** (HTML, CSS, JavaScript, modules ES, sans build), comme demandé après la revue du prof :

| | Front office | Back office |
|---|---|---|
| Pour qui | Conducteurs | Opérateurs du parking |
| Adresse | `/` (`https://smartparking-supcom.duckdns.org/`) | `/admin/` (`https://smartparking-supcom.duckdns.org/admin/`) |
| Dossier | `front-office/` | `back-office/` |
| Comptes acceptés | `DRIVER` uniquement (inscription possible) | `OPERATOR` uniquement, connexion propre, pas d'inscription |
| Contenu | accueil, connexion et inscription, espace conducteur, plan en temps réel ; puis réservation et guidage | tableau de bord, plan en temps réel, calibration des places ; puis alertes, comptes, statistiques |
| Service worker | `/service-worker.js` (portée `/`, ignore `/admin/`) | `/admin/service-worker.js` (portée `/admin/`) |
| Session | `sessionStorage`, clés `sp.driver.*` | `sessionStorage`, clés `sp.operator.*` |

Un compte de l'autre rôle est refusé à la connexion (« Ce compte n'a pas accès au back office », ou renvoi vers `/admin/` pour un opérateur sur le front office). La **vraie** protection reste le serveur : chaque route vérifie le rôle du JWT (`@RolesAllowed`).

Tous les noms techniques sont en anglais (fichiers, fonctions, champs JSON, classes CSS) ; les textes affichés restent en français.

## Structure

```
frontend/
├── front-office/                 # servi sur /
│   ├── index.html                # accueil
│   ├── login.html                # connexion et inscription (js/login.js)
│   ├── dashboard.html            # espace conducteur
│   ├── live-map.html             # plan en temps réel
│   ├── offline.html, manifest.json, service-worker.js
│   └── js/home.js, js/login.js
├── back-office/                  # servi sur /admin/
│   ├── index.html                # connexion opérateur (js/login.js)
│   ├── dashboard.html            # tableau de bord
│   ├── live-map.html             # plan en temps réel
│   ├── calibration.html          # dessin des polygones (js/calibration.js, js/spot-validation.js, css/calibration.css)
│   └── offline.html, manifest.json, service-worker.js
├── shared/                       # servi sur /shared/, utilisé par les deux applications
│   ├── js/session.js             # session : JWT, rôle de l'application, appels à l'API, connexion, déconnexion
│   ├── js/protected-page.js      # pages réservées : contrôle du rôle, nom, bouton de déconnexion
│   ├── js/pwa.js                 # service worker de l'application, bouton « Installer », état du réseau
│   ├── js/realtime.js            # état des places en temps réel (GET /api/spots + WebSocket), sans dessin
│   ├── js/live-map.js            # plan SVG provisoire branché sur realtime.js
│   ├── css/base.css, css/parking-map.css
│   └── icons/                    # icônes 192, 512, maskable + generate_icons.py
└── tools/                        # outils de test, jamais déployés
    ├── fake_api.py               # fausse API : fichiers des deux applications, auth, polygones
    ├── fake_realtime.py          # fausse API + GET /api/spots + WebSocket /api/ws/spots
    ├── validate_spots.py         # vérifie un places.json sur le disque
    ├── read_polygons.py          # relit les polygones enregistrés sur un serveur
    └── test_*.py, test_*.js/mjs  # tests automatiques (lancés par la CI)
```

Chaque page dit à quelle application elle appartient : `<html data-app="front-office">` ou `data-app="back-office"`. `shared/js/session.js` en déduit le rôle accepté, les clés de session et la page de connexion.

## Lancer en local (sans le vrai serveur)

```bash
python frontend/tools/fake_realtime.py            # depuis la racine du dépôt ; Ctrl+C pour arrêter
```

- Front office : http://localhost:8000/ → *Se connecter* avec `conducteur@exemple.tn` / `conducteur123` (ou créer un compte).
- Back office : http://localhost:8000/admin/ → `operateur@exemple.tn` / `operateur123`.

La fausse API sert les dossiers comme Nginx (`/`, `/admin/`, `/shared/`) et implémente `register`, `login`, `me`, `GET` et `PUT /api/config/polygons`, `GET /api/spots` et le WebSocket `/api/ws/spots` (un changement aléatoire toutes les 2 s). Bibliothèque standard de Python uniquement. Jetons de test acceptés en plus : `operator` et `driver`.

Avec le vrai serveur (`server/`, `docs/installation-serveur.md`) : https://localhost/ et https://localhost/admin/.

## Tests automatiques

```bash
node frontend/tools/test_spot_validation.js            # validation des polygones côté client
node frontend/tools/test_realtime.mjs                  # module temps réel (faux WebSocket, fausse minuterie)
python -m unittest frontend/tools/test_fake_api.py -v       # fausse API : fichiers des deux applications, auth, polygones
python -m unittest frontend/tools/test_fake_realtime.py -v  # faux serveur temps réel (vrai WebSocket)
```

Ils tournent aussi dans la CI (`.github/workflows/ci.yml`), avec une vérification de syntaxe de tous les fichiers JavaScript.

À la main : DevTools > Application (manifeste sans erreur, service worker « activated » pour `/` et pour `/admin/`), puis Network > Offline et recharger (la page hors ligne de chaque application s'affiche).

## Modifier une coquille

Un fichier ajouté ou modifié dans la coquille d'une application doit être ajouté à `SHELL` dans **son** `service-worker.js`, avec `CACHE_VERSION` incrémenté. Chaque service worker ne supprime que ses propres caches (`sp-front-*` ou `sp-back-*`). Les appels à l'API (`/api/...`) ne sont jamais mis en cache.

## Session et appels à l'API (`shared/js/session.js`)

- `login(email, password)` : `POST /api/auth/login`, réponse `{access_token, type, expires_in, user}` (contrat §2.5). La session n'est ouverte que si `user.role` est le rôle de l'application.
- `register(name, email, password)` : `POST /api/auth/register` (front office seulement), puis connexion.
- `callApi(path, {method, body})` : ajoute `Authorization: Bearer <jwt>` ; sur un `401`, la session est effacée et la page revient à la connexion de l'application, avec « Votre session a expiré ».
- Le jeton est gardé dans `sessionStorage` (jamais `localStorage`, jamais dans une URL, contrat §1.2).

## Calibration des places (back office, `/admin/calibration.html`)

Page de l'opérateur pour indiquer au Pi où se trouvent les places sur l'image de la caméra. On dessine chaque place et chaque zone interdite sur la photo de référence ; la page enregistre `places.json` sur le serveur (`PUT /api/config/polygons`, contrat §2.10) au format du contrat des polygones (§5.2 : `parking`, `resolution`, `spots[id, type, max_duration_min, polygon]`, `forbidden_zones[id, polygon]`). Elle peut aussi l'exporter. À refaire à chaque fois que la caméra ou la maquette bouge.

1. Au chargement, la page récupère les polygones déjà enregistrés (`GET /api/config/polygons`) ; s'il n'y en a pas, on part d'un plan vide.
2. **Choisir la photo** : photo de référence prise par le Pi, en 640 × 480 (une autre résolution affiche un avertissement).
3. Pour chaque place : cliquer sur ses 4 coins, puis **Fermer le polygone**. Le formulaire propose le nom et le type suivants selon le plan (A1 `accessible` … B4 `electric`). Seules les places du plan (A1 à B4) sont acceptées, comme sur le serveur.
4. Zone interdite : même chose, en cochant « une zone interdite » (nom proposé : `allee1`).
5. **Modifier** / **Supprimer** dans la liste ; « Modifier le tracé » pour retoucher les points.
6. **Enregistrer sur le serveur** : vérification locale avec les mêmes règles que le serveur (`back-office/js/spot-validation.js`), puis envoi du fichier complet. En cas de refus (`400 invalid_config`), la liste des problèmes renvoyée par le serveur est affichée.

Raccourcis : Entrée ferme le polygone, Retour arrière annule le dernier point, Échap efface le tracé.

Relire ou vérifier un fichier :

```bash
python frontend/tools/read_polygons.py                                          # fausse API
python frontend/tools/read_polygons.py --url https://localhost --cacert server/certs/ca.pem --token <jwt-operateur>
python frontend/tools/validate_spots.py chemin/vers/places.json
```

Limites : le plan (A1 à B4) est écrit dans `calibration.js` et dans les outils (plus tard : `GET /api/config/layout`) ; un coin ne se déplace pas à la souris (« Modifier le tracé » ou redessiner). Ne pas versionner `places.json` ni les photos de test.

## Plan en temps réel (`live-map.html`, les deux applications)

Contrat : `docs/contrats.md` §1.4 (WebSocket) et §2.6 (`GET /api/spots`).

`shared/js/realtime.js` est un **module réutilisable, sans dessin** :

1. il ouvre `ws(s)://<hôte>/api/ws/spots?token=<jwt>` ;
2. dès que le WebSocket est ouvert, il charge l'**état complet** (`GET /api/spots`), puis applique les messages `{ "spot": "A3", "state": "occupied", "since": "…Z" }` ; ceux reçus pendant le chargement sont gardés puis appliqués, sauf s'ils sont plus anciens ;
3. si le WebSocket se coupe, il se **reconnecte tout seul** (1 s, 2 s, 5 s, puis toutes les 10 s) et recharge l'état complet ; au retour du réseau, il réessaie tout de suite ;
4. statuts : `connecting`, `connected`, `reconnecting`, `offline` ;
5. `401` (jeton absent ou expiré, `GET /api/spots` en 401, WebSocket fermé avec `4401` ou `1008`) : session effacée, retour à la connexion ;
6. un message avec un champ `type` (événements, statut du Pi : proposition du §2.6) est transmis à `onOtherMessage`.

Convention pour le plan SVG définitif (généré à partir de `GET /api/parking`) : chaque place porte **`data-spot="<id>"`** ; le module y met **une seule classe** parmi `free`, `occupied` et `reserved` (plus `data-state`). Les couleurs sont dans `shared/css/parking-map.css`.

```js
import { connectMap, showStatus } from '/shared/js/realtime.js';

const realtime = connectMap(document.querySelector('#map svg'), {
  onStatus: showStatus(document.querySelector('#realtime-status')),
  onChange(spot, state, info) { /* facultatif */ },
  onFullState(spots) { /* facultatif : Map id -> { id, state, since, type, max_duration_min } */ },
});
// realtime.spots (Map), realtime.status, realtime.stop()
```

Test à la main avec `fake_realtime.py` : se connecter, ouvrir le plan, voir une place changer toutes les 2 s ; arrêter le serveur (« Reconnexion… », plan grisé), le relancer (« Temps réel : connecté », sans recharger la page).

Limites : le dessin de `shared/js/live-map.js` est **provisoire** (remplacé par le plan généré depuis `GET /api/parking`) ; le module `realtime.js`, lui, reste.

## Régénérer les icônes

```bash
pip install pillow
python frontend/shared/icons/generate_icons.py
```
