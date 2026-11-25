# Contrats d'interface

Les trois parties du projet (IoT, serveur, PWA) sont développées en parallèle par des personnes différentes. Un **contrat** fixe ce qu'une partie envoie ou attend d'une autre : tant qu'il est respecté, chacune avance sans attendre les autres, avec des données simulées si besoin.

**Noms en anglais.** Depuis la refonte demandée par le prof, tous les noms techniques (routes, champs JSON, valeurs, codes d'erreur, collections, topics MQTT, variables d'environnement) sont en anglais, selon `docs/proposition-contrat-anglais.md` validée par les trois. Les textes montrés aux utilisateurs (`message`, `title`, `body`) restent en français.

**Un seul fichier, une section par partie.** Chaque section est écrite par la personne du domaine, puis relue par les deux autres. Un contrat ne change jamais sans l'accord des trois : on ouvre une PR qui ne touche que sa section et on prévient dans le groupe.

| Section | Rédaction | Statut |
|---|---|---|
| [1. Contrat PWA](#1-contrat-pwa) | Eya | Brouillon, à relire |
| [2. Contrat API REST et base MongoDB](#2-contrat-api-rest-et-base-mongodb) | Maissa (serveur) | Proposition, à relire par Eya et Amal |
| [3. Contrat des notifications push](#3-contrat-des-notifications-push) | Maissa (serveur) | Proposition, à relire par Eya |
| [4. Contrat plan de la maquette](#4-contrat-plan-de-la-maquette) | Maissa, avec Amal (IoT) | Proposition, à relire par Amal |
| [5. MQTT et polygones](#5-mqtt-et-polygones) | Amal (IoT) | Repris de `iot/README.md`, validé |

Ordre de lecture conseillé pour le serveur : 5 (ce que le Pi envoie), puis 2 et 3 (ce que le serveur expose), puis 4 (le plan qui sert au guidage).

---

## 1. Contrat PWA

Rédaction : Eya · Statut : brouillon, à relire par Amal et Maissa

La PWA est la seule application cliente. Cette section dit **ce qu'elle attend du serveur et de la maquette**, et **ce qu'elle fournit**. Le détail exact des routes (champs, codes d'erreur) est écrit par le serveur dans la section 2 ; ici on liste les besoins de la PWA pour que la section 2 les couvre.

### 1.1 Hébergement et conventions

- La PWA est servie en **HTTPS** par la VM, sur **le même domaine que l'API** (pas de CORS à gérer). Un service worker n'est actif qu'en HTTPS ou sur `localhost`.
- **Toutes les routes de l'API sont sous `/api`**, et le WebSocket sous `/api/ws`. Le service worker de la PWA ne met jamais en cache ce qui commence par `/api`.
- Format JSON en UTF-8. Dates en **ISO 8601 UTC** avec `Z` (`2026-11-20T10:02:11Z`) ; la PWA convertit en heure locale à l'affichage.
- Identifiants : parking `P1`, places `A1`, `A2`… (comme dans `places.exemple.json`), zones interdites `allee1`…
- Erreurs : code HTTP adapté (400, 401, 403, 404, 409) et corps `{"error": "code_court", "message": "texte lisible"}`. La PWA affiche `message` à l'utilisateur.
- Noms d'anomalie identiques partout (MQTT, API, push) : `straddling`, `overstay`, `unauthorized_special_spot`, `forbidden_zone`.

### 1.2 Authentification

- La PWA envoie `Authorization: Bearer <jwt>` sur chaque appel.
- Le JWT contient l'identifiant de l'utilisateur (`sub`), son rôle (`role` : `DRIVER` ou `OPERATOR`) et une expiration (`exp`). La PWA lit le `role` pour choisir l'interface à afficher ; **la sécurité reste côté serveur**, qui vérifie le rôle à chaque appel.
- Si l'API répond `401`, la PWA efface le jeton et renvoie vers la page de connexion. Si elle répond `403`, elle affiche « accès refusé ».
- Le jeton est gardé dans `sessionStorage` (pas dans `localStorage`) ; il n'est jamais mis dans une URL.

### 1.3 Ce dont la PWA a besoin de l'API

| Écran | Besoin | Rôle |
|---|---|---|
| Inscription, connexion | Créer un compte, obtenir un JWT | public |
| Plan du parking (les deux) | Liste des places avec leur état (`free`, `occupied`, `reserved`), type (`standard`, `accessible`, `electric`) et polygone | tous |
| Plan en temps réel | Flux WebSocket des changements (voir 1.4) | tous |
| Réservation | Créer, annuler, lister ses réservations, payer (simulé) | conducteur |
| Guidage | Trajet de l'entrée vers la place libre la plus proche (liste de points du graphe) et verrouillage de la place | conducteur |
| Tableau de bord | État global : nombre de places libres/occupées, statut du Pi | exploitant |
| Alertes | Liste des événements d'anomalie, du plus récent au plus ancien | exploitant |
| Configuration | Lire et modifier les places (type, durée maximale), les zones, les polygones | exploitant |
| Utilisateurs et autorisations | Lister, créer, modifier, désactiver ; gérer les autorisations PMR/électrique | exploitant |
| Statistiques | Taux d'occupation, heures de pointe, durée moyenne, nombre d'infractions | exploitant |
| Notifications | Enregistrer / supprimer un abonnement push (voir 1.5) | tous |

### 1.4 Temps réel (WebSocket)

- Connexion : `/api/ws/spots?token=<jwt>` (un navigateur ne peut pas mettre d'en-tête `Authorization` sur un WebSocket). Le serveur refuse la connexion sans JWT valide.
- Message reçu à chaque changement d'état d'une place :

```json
{ "spot": "A3", "state": "occupied", "since": "2026-11-20T10:02:11Z" }
```

- À la connexion, la PWA charge d'abord l'état complet par l'API REST, puis applique les messages WebSocket. Si le WebSocket se coupe, elle se reconnecte toute seule et recharge l'état complet.

### 1.5 Notifications push

Web Push avec clés VAPID. La clé **privée** reste dans une variable d'environnement sur la VM, **jamais dans Git**.

**Routes à fournir par le serveur :**

| Route | Rôle | Description |
|---|---|---|
| `GET /api/push/public-key` | public | Renvoie `{"key": "<clé publique VAPID en base64url>"}` |
| `POST /api/push/subscriptions` | connecté | Le corps est l'objet `PushSubscription` du navigateur, tel quel (`endpoint`, `keys.p256dh`, `keys.auth`). `201` à la création, `200` si l'`endpoint` existe déjà (appel idempotent). |
| `DELETE /api/push/subscriptions` | connecté | Corps `{"endpoint": "..."}`, réponse `204`. |

Un utilisateur peut avoir plusieurs abonnements. Si le service de push répond `404` ou `410` à un envoi, le serveur supprime l'abonnement.

**Message envoyé à la PWA** (4 Ko maximum) :

```json
{
  "type": "straddling",
  "title": "Voiture à cheval",
  "body": "Une voiture occupe les places A3 et A4.",
  "url": "/exploitant#alertes",
  "ts": "2026-11-20T10:02:11Z",
  "id": "evt_8f3a",
  "data": { "spots": ["A3", "A4"] }
}
```

`type`, `title`, `body`, `url` (chemin relatif dans la PWA), `ts` et `id` sont obligatoires ; `id` sert à ne pas afficher deux fois la même notification.

| `type` | Destinataire | Déclenché quand |
|---|---|---|
| `reservation_confirmed` | Conducteur | La réservation est créée |
| `time_almost_up` | Conducteur | Il reste peu de temps avant la fin de la durée autorisée |
| `spot_overdue` | Conducteur | La durée autorisée est dépassée |
| `spot_taken` | Conducteur | La place proposée par le guidage a été prise, le trajet est recalculé |
| `straddling` | Exploitant | Une voiture est à cheval sur deux places |
| `overstay` | Exploitant | Durée autorisée dépassée ou véhicule présent plusieurs jours |
| `unauthorized_special_spot` | Exploitant | Place PMR, électrique ou réservée occupée sans autorisation |
| `forbidden_zone` | Exploitant | Un véhicule est dans une zone interdite |

Les noms `straddling` et `forbidden_zone` sont ceux du contrat MQTT ; `overstay` et `unauthorized_special_spot` sont calculés par le serveur.

**Côté PWA** (service worker) : à la réception, affichage de la notification avec `title` et `body` (un même `id` remplace la précédente) ; au clic, ouverture ou mise au premier plan de la PWA sur `url`. La permission n'est demandée qu'après une action de l'utilisateur (bouton « Activer les notifications »). Sur iPhone, la PWA doit d'abord être ajoutée à l'écran d'accueil.

### 1.6 Outil de calibration (polygones)

La PWA fournit l'outil qui dessine les places sur une photo de la maquette. Il produit un fichier au format de `iot/places.exemple.json` (`parking`, `resolution`, `spots[id, type, max_duration_min, polygon]`, `forbidden_zones`), coordonnées **en pixels de l'image de la caméra**. Le serveur l'enregistre et le Pi le lit : le format est donc celui de la section 5.

### 1.7 Ce que la PWA attend de la maquette (section 4)

Pour dessiner le plan SVG et le trajet : la **liste des places** avec leur numéro, la **résolution de l'image** de la caméra et le **graphe** du parking (nœuds, coordonnées sur le plan, arêtes). Sans graphe, le trajet n'est pas affichable.

### 1.8 Questions ouvertes

1. Le conducteur reçoit-il `overstay` quand c'est son véhicule ? (proposition : non, seulement `spot_overdue`)
2. Délai de `time_almost_up` : 10 minutes avant la fin ? (proposition : oui)
3. Adresse d'équipe pour le sujet VAPID (`mailto:`) : laquelle ?
4. L'état `reserved` d'une place : porté par l'API des places, ou à déduire des réservations ?

---

## 2. Contrat API REST et base MongoDB

Rédaction : Maissa (serveur) · Statut : proposition, à relire par Eya (PWA) et Amal (IoT)

Cette section répond aux besoins listés en 1.3 à 1.5. Les noms des routes et des champs sont en anglais (voir l'en-tête de ce fichier).

### 2.1 Conventions

- **URL de base** : `https://smartparking-supcom.duckdns.org/api` (VM AWS ; l'API y sera déployée au sprint 2) et `https://localhost/api` en local (`server/`, voir `docs/installation-serveur.md`).
- Préfixe **`/api`**, JSON en UTF-8, dates ISO 8601 UTC avec `Z` (voir 1.1).
- Noms des champs en `snake_case`, sans accents (`max_duration_min`, `password`).
- Un seul parking dans le projet : `P1`. Les routes ne le répètent pas ; il figure dans les réponses quand c'est utile.
- Identifiants : places `A1`…`B4`, nœuds du graphe `E`, `I1`…, `S` (section 4), utilisateurs `u_…`, réservations `r_…`, événements `evt_…`, autorisations `perm_…`.
- **Erreurs** : toujours `{"error": "code_court", "message": "texte lisible"}`. Codes communs à toutes les routes :

| HTTP | `error` | Quand |
|---|---|---|
| 400 | `invalid_request` | JSON mal formé, champ manquant ou hors limites (le `message` dit lequel) |
| 401 | `unauthenticated` | JWT absent, invalide ou expiré |
| 403 | `forbidden` | Le rôle ne permet pas cette route |
| 404 | `not_found` | L'objet demandé n'existe pas |
| 500 | `server_error` | Erreur interne (détail dans les journaux du serveur, jamais dans la réponse) |

Les codes propres à chaque route (souvent `409`) sont donnés avec la route.

### 2.2 Authentification et JWT

- En-tête sur chaque appel protégé : `Authorization: Bearer <jwt>`.
- JWT signé **RS256** par le module IAM (format MicroProfile JWT, pris en charge par WildFly), avec un **`kid`** dans l'en-tête. Les clés privées sont des fichiers `<kid>.pem` sur la VM (`JWT_KEYS_DIR`), jamais dans Git.
- **JWKS** : `GET /.well-known/jwks.json` (public) publie les clés publiques au format JWK (`kty`, `n`, `e`, `kid`, `use: sig`, `alg: RS256`). L'API vérifie les jetons avec ces clés, choisies d'après le `kid` ; c'est ce qui permet la rotation des clés sans redémarrage.
- Contenu du jeton :

```json
{
  "iss": "smartparking",
  "sub": "u_6543a1f0",
  "upn": "sara@exemple.tn",
  "role": "DRIVER",
  "groups": ["DRIVER"],
  "iat": 1795168800,
  "exp": 1795172400
}
```

`role` est lu par la PWA (1.2) ; `groups` est lu par WildFly pour `@RolesAllowed`. Durée de vie : **1 heure**. Pas de jeton de rafraîchissement : à l'expiration, l'utilisateur se reconnecte.

Mots de passe : hachés en **Argon2id** (format PHC `$argon2id$v=19$m=19456,t=2,p=1$…`, paramètres OWASP).

Colonne « Rôle » des tableaux : **public** = sans jeton ; **connecté** = `DRIVER` ou `OPERATOR` ; sinon le rôle exigé.

### 2.3 Vue d'ensemble des routes

| Méthode | Chemin | Rôle | Objet | Sprint |
|---|---|---|---|---|
| GET | `/api/health` | public | Le serveur répond (test de déploiement) | 1 |
| GET | `/.well-known/jwks.json` | public | Clés publiques des JWT (2.2) | 4 |
| POST | `/api/auth/register` | public | Créer un compte conducteur | 3 |
| POST | `/api/auth/login` | public | Obtenir un JWT | 3 |
| GET | `/api/auth/me` | connecté | Mon profil et mes autorisations | 3 |
| GET | `/api/parking` | connecté | Plan : dimensions, places, zones, graphe (section 4) | 4 |
| GET | `/api/spots` | connecté | Toutes les places avec leur état | 4 |
| GET | `/api/spots/{id}` | connecté | Détail d'une place | 4 |
| WS | `/api/ws/spots?token=<jwt>` | connecté | Changements d'état en temps réel (1.4) | 4 |
| POST | `/api/guidance` | DRIVER | Place libre la plus proche, trajet et verrouillage | 7–8 |
| GET | `/api/guidance` | DRIVER | Mon guidage en cours | 8 |
| DELETE | `/api/guidance` | DRIVER | Abandonner le guidage (libère le verrou) | 8 |
| POST | `/api/guidance/arrival` | DRIVER | Confirmer « Je suis garé » sur la place proposée | 8 |
| POST | `/api/reservations` | DRIVER | Réserver une place | 6 |
| GET | `/api/reservations` | connecté | Mon historique (exploitant : toutes) | 6–7 |
| GET | `/api/reservations/{id}` | connecté | Détail d'une réservation | 6 |
| DELETE | `/api/reservations/{id}` | DRIVER | Annuler ma réservation | 6 |
| POST | `/api/reservations/{id}/payment` | DRIVER | Payer (simulé) | 7 |
| GET | `/api/dashboard` | OPERATOR | Résumé, alertes non traitées, statut du Pi | 5 |
| GET | `/api/events` | OPERATOR | Liste des anomalies | 8 |
| PATCH | `/api/events/{id}` | OPERATOR | Marquer comme traitée | 8 |
| GET, PUT | `/api/config/polygons` | OPERATOR | Fichier `places.json` (section 5.2) | 3 |
| GET, PUT | `/api/config/layout` | OPERATOR | Plan et graphe (section 4) | 6 |
| PATCH | `/api/config/spots/{id}` | OPERATOR | Type et durée maximale d'une place | 6 |
| GET, PATCH | `/api/config/settings` | OPERATOR | Tarif et délais | 6 |
| GET | `/api/users` | OPERATOR | Liste des comptes | 6 |
| POST | `/api/users` | OPERATOR | Créer un compte (dont exploitant) | 6 |
| PATCH | `/api/users/{id}` | OPERATOR | Modifier, désactiver | 6 |
| POST | `/api/users/{id}/permissions` | OPERATOR | Donner une autorisation PMR ou électrique | 6 |
| DELETE | `/api/users/{id}/permissions/{pid}` | OPERATOR | Retirer une autorisation | 6 |
| GET | `/api/stats` | OPERATOR | Statistiques | 9 |
| GET | `/api/push/public-key` | public | Clé publique VAPID (1.5) | 9 |
| POST | `/api/push/subscriptions` | connecté | Enregistrer un abonnement (1.5) | 9 |
| DELETE | `/api/push/subscriptions` | connecté | Supprimer un abonnement (1.5) | 9 |

### 2.4 Santé

**`GET /api/health`** · public → `200`

```json
{ "status": "ok", "version": "0.1.0", "mongo": "ok", "mqtt": "ok", "ts": "2026-11-06T14:00:00Z" }
```

`mongo` et `mqtt` valent `ok` ou `ko`. Si l'un des deux vaut `ko`, la réponse est `503` avec le même corps. C'est la page de test du déploiement (sprint 1).

### 2.5 Authentification

**`POST /api/auth/register`** · public

```json
{ "name": "Sara Ben Ali", "email": "sara@exemple.tn", "password": "motdepasse8" }
```

→ `201`

```json
{ "id": "u_6543a1f0", "name": "Sara Ben Ali", "email": "sara@exemple.tn", "role": "DRIVER", "active": true }
```

Erreurs : `400 invalid_request` (email invalide, mot de passe de moins de 8 caractères), `409 email_already_exists`.
Le rôle est toujours `DRIVER` : un champ `role` envoyé ici est ignoré.

**`POST /api/auth/login`** · public

```json
{ "email": "sara@exemple.tn", "password": "motdepasse8" }
```

→ `200`

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIs...",
  "type": "Bearer",
  "expires_in": 3600,
  "user": { "id": "u_6543a1f0", "name": "Sara Ben Ali", "email": "sara@exemple.tn", "role": "DRIVER" }
}
```

Erreurs : `401 invalid_credentials` (même message que l'email ou le mot de passe soit faux), `403 account_disabled`.

**`GET /api/auth/me`** · connecté → `200`

```json
{
  "id": "u_6543a1f0", "name": "Sara Ben Ali", "email": "sara@exemple.tn", "role": "DRIVER",
  "permissions": [ { "id": "perm_11", "type": "accessible", "end": null } ]
}
```

### 2.6 Places et temps réel

**`GET /api/parking`** · connecté → `200` : le JSON du plan décrit en **4.3** (dimensions, places, zones, graphe). La PWA s'en sert pour dessiner le plan SVG et le trajet.

**`GET /api/spots`** · connecté → `200`

```json
{
  "parking": "P1",
  "ts": "2026-11-20T10:05:00Z",
  "summary": { "total": 8, "free": 5, "occupied": 2, "reserved": 1 },
  "spots": [
    { "id": "A1", "type": "accessible",        "state": "free",    "since": "2026-11-20T08:00:00Z", "max_duration_min": 120 },
    { "id": "A2", "type": "standard",    "state": "occupied",  "since": "2026-11-20T10:02:11Z", "max_duration_min": 120 },
    { "id": "B4", "type": "electric", "state": "reserved", "since": "2026-11-20T09:58:40Z", "max_duration_min": 60 }
  ]
}
```

- `state` : `free`, `occupied` ou `reserved`. **Le serveur calcule `reserved`** (réponse à la question 4 de 1.8) : une place libre avec une réservation active est renvoyée `reserved`. Le Pi, lui, ne connaît que `free` et `occupied`.
- Une place **verrouillée par le guidage** reste affichée `free` (elle l'est réellement) ; elle n'est simplement pas proposée à un autre conducteur.

**`GET /api/spots/{id}`** · connecté → `200` : les mêmes champs, plus `overdue` (booléen, durée maximale dépassée) et, pour l'exploitant seulement, `lock` (`null` ou `{"until": "..."}`). Erreur : `404 not_found`.

**WebSocket `/api/ws/spots?token=<jwt>`** : format défini en 1.4. Il envoie aussi l'état `reserved`.

> **Proposition d'ajout (à valider par Eya)** : les exploitants reçoivent en plus, sur le même WebSocket, des messages avec un champ `type`. Un message **sans** `type` reste un changement de place (format de 1.4, inchangé).
>
> ```json
> { "type": "event", "event": { "id": "evt_8f3a", "type": "straddling", "spots": ["A3", "A4"], "start": "2026-11-20T10:02:11Z" } }
> { "type": "pi_status", "online": true, "fps": 0.8, "cpu_temp": 61, "received_at": "2026-11-20T10:05:00Z" }
> ```

### 2.7 Guidage

**`POST /api/guidance`** · DRIVER

```json
{ "from": "E" }
```

Le corps est facultatif (`from` vaut `E`, l'entrée, par défaut).

→ `200`

```json
{
  "spot": "A2",
  "distance_cm": 29,
  "path": ["E", "I1", "I2", "A2"],
  "locked_until": "2026-11-20T10:08:00Z"
}
```

Règles :
- Plus court chemin (Dijkstra) dans le graphe de la section 4, vers les places `free`, non verrouillées par un autre conducteur et non réservées.
- Places `accessible` ou `electric` : proposées seulement si le conducteur a l'autorisation correspondante, valide.
- À égalité de distance, l'identifiant le plus petit dans l'ordre alphabétique gagne (`A1` avant `B1`).
- La place est **verrouillée 3 minutes** (paramètre `lock_min`). Un conducteur n'a qu'un verrou : un nouvel appel pendant le verrou renvoie la même place.
- Le verrou disparaît à l'expiration, par `DELETE /api/guidance`, ou quand l'arrivée du conducteur est confirmée (voir ci-dessous).

**Confirmation d'arrivée** (décision d'équipe du 16 novembre 2026). La caméra ne reconnaît ni plaques ni visages : quand la place verrouillée devient `occupied`, le serveur ne sait pas **qui** s'est garé. C'est donc le conducteur qui le confirme, avec le bouton « Je suis garé » de la PWA (`POST /api/guidance/arrival`).
- **Arrivée confirmée** : si la place devient `occupied` et que le conducteur confirme, dans un sens ou dans l'autre, à moins de `arrival_confirmation_s` secondes d'écart (60 par défaut), le guidage est terminé et le verrou supprimé.
- **Pas de confirmation** : si la place devient `occupied` et que le conducteur ne confirme pas dans les `arrival_confirmation_s` secondes, le serveur considère que **quelqu'un d'autre** a pris la place : il calcule un nouveau trajet, verrouille la nouvelle place et envoie la notification `spot_taken` (section 3).
- **Confirmation sans occupation** : si le conducteur confirme mais que la place reste `free`, rien ne change ; le verrou expire normalement (le conducteur s'est peut-être garé ailleurs).

Erreurs : `409 parking_full` (aucune place possible pour ce conducteur), `400 invalid_request` (`from` inconnu).

**`GET /api/guidance`** · DRIVER → `200` avec le même corps, ou `404 not_found` s'il n'y a pas de guidage en cours.
**`DELETE /api/guidance`** · DRIVER → `204`.
**`POST /api/guidance/arrival`** · DRIVER (corps vide) → `200` `{ "spot": "A2", "confirmed_at": "2026-11-20T10:06:30Z" }`. Erreur : `404 not_found` s'il n'y a pas de guidage en cours.

### 2.8 Réservations et paiement simulé

États d'une réservation :

```
active ──(la voiture arrive : place occupee)──▶ en_cours ──(la voiture part)──▶ terminee ──(paiement)──▶ payee
  │                                                │
  ├──(annulation)──▶ annulee                       └──(paiement avant le départ)──▶ payee
  └──(15 min sans arrivée)──▶ expiree
```

**`POST /api/reservations`** · DRIVER

```json
{ "spot": "B2" }
```

→ `201`

```json
{
  "id": "r_7c21", "spot": "B2", "state": "active",
  "created_at": "2026-11-20T09:58:40Z", "expires_at": "2026-11-20T10:13:40Z",
  "start": null, "end": null, "amount": null, "payment": null
}
```

Erreurs : `409 spot_unavailable` (place pas libre, ou verrouillée pour un autre conducteur), `409 reservation_already_active` (une seule réservation `active` ou `in_progress` par conducteur), `403 permission_required` (place PMR ou électrique sans autorisation), `404 not_found` (place inconnue).

**`GET /api/reservations?state=paid&limit=20`** · connecté → `200` : liste du plus récent au plus ancien, objets au format ci-dessus. Le conducteur ne voit que les siennes ; l'exploitant voit toutes, filtrables avec `user=u_…`. Paramètres facultatifs : `state`, `limit` (20 par défaut, 100 au plus).

**`GET /api/reservations/{id}`** · connecté → `200`. Erreur : `404 not_found` (y compris pour la réservation d'un autre conducteur, pour ne pas révéler qu'elle existe).

**`DELETE /api/reservations/{id}`** · DRIVER → `200` avec la réservation à l'état `cancelled`. Elle reste dans l'historique. Erreurs : `404 not_found`, `409 cancellation_not_allowed` (état autre que `active`).

**`POST /api/reservations/{id}/payment`** · DRIVER

```json
{ "method": "simulated" }
```

→ `200`

```json
{
  "id": "r_7c21", "spot": "B2", "state": "paid",
  "start": "2026-11-20T10:01:05Z", "end": "2026-11-20T11:20:30Z",
  "amount": 2.25, "currency": "TND",
  "payment": { "method": "simulated", "paid_at": "2026-11-20T11:21:00Z", "receipt": "RECU-2026-000042" }
}
```

- Montant = `hourly_rate` × durée arrondie au quart d'heure supérieur (1 quart d'heure au minimum). Si la voiture est encore là (`in_progress`), la durée va jusqu'au moment du paiement.
- **Paiement simulé** : aucune donnée bancaire n'est demandée ni stockée ; `method` vaut toujours `simulated`.

Erreurs : `409 already_paid`, `409 payment_not_allowed` (état `active`, `cancelled` ou `expired`).

### 2.9 Exploitant : tableau de bord et événements

**`GET /api/dashboard`** · OPERATOR → `200`

```json
{
  "ts": "2026-11-20T10:05:00Z",
  "summary": { "total": 8, "free": 5, "occupied": 2, "reserved": 1 },
  "occupancy_rate": 0.375,
  "unhandled_alerts": 2,
  "pi": { "online": true, "fps": 0.8, "cpu_temp": 61, "received_at": "2026-11-20T10:04:12Z" }
}
```

`occupancy_rate` = (occupées + réservées) / total. Le Pi est affiché `online: false` s'il n'a rien envoyé depuis 3 minutes (paramètre `pi_offline_min`).

**`GET /api/events?handled=false&type=straddling&from=2026-11-20&to=2026-11-21&limit=50`** · OPERATOR → `200` (tous les paramètres sont facultatifs ; tri du plus récent au plus ancien)

```json
[
  {
    "id": "evt_8f3a", "type": "straddling", "spots": ["A3", "A4"], "zone": null,
    "start": "2026-11-20T10:02:11Z", "end": null, "source": "pi",
    "handled": false, "handled_by": null, "handled_at": null, "comment": null
  },
  {
    "id": "evt_8f40", "type": "overstay", "spots": ["A2"], "zone": null,
    "start": "2026-11-20T12:02:11Z", "end": null, "source": "server",
    "details": { "duration_min": 121, "max_duration_min": 120 },
    "handled": false, "handled_by": null, "handled_at": null, "comment": null
  }
]
```

- `type` : `straddling`, `forbidden_zone` (envoyés par le Pi), `overstay`, `unauthorized_special_spot` (calculés par le serveur).
- `end` est rempli quand le serveur sait que l'anomalie est finie (la place redevient libre) ; sinon `null`.

**`PATCH /api/events/{id}`** · OPERATOR

```json
{ "handled": true, "comment": "Conducteur prévenu" }
```

→ `200` avec l'événement mis à jour (`handled_by`, `handled_at` remplis par le serveur). Erreur : `404 not_found`.

### 2.10 Exploitant : configuration

**`GET /api/config/polygons`** → `200` : le fichier `places.json` au format de **5.2**.
**`PUT /api/config/polygons`** : corps = ce même fichier, en entier. → `200` avec le fichier enregistré. Le serveur le publie au Pi sur `parking/P1/config` (proposition 5.3), pour que le Pi le reçoive sans redémarrage.
Erreur `400 invalid_config` si : identifiant en double, polygone de moins de 3 points ou en dehors de `resolution`, `type` inconnu, `max_duration_min` ≤ 0, place absente du plan.

**`GET /api/config/layout`** → `200` : JSON de **4.3**. **`PUT /api/config/layout`** : corps = ce JSON. Erreur `400 invalid_config` si le graphe n'est pas connexe, si une place n'a pas de nœud ou si le nœud `E` manque.

**`PATCH /api/config/spots/{id}`**

```json
{ "type": "electric", "max_duration_min": 60 }
```

→ `200` avec la place. Les deux champs sont facultatifs. Le changement est aussi republié au Pi.

**`GET /api/config/settings`** → `200`. **`PATCH /api/config/settings`** : un ou plusieurs champs → `200`.

```json
{
  "hourly_rate": 1.5, "currency": "TND",
  "lock_min": 3, "arrival_confirmation_s": 60, "reservation_timeout_min": 15,
  "end_warning_min": 10, "pi_offline_min": 3
}
```

### 2.11 Exploitant : utilisateurs et autorisations

**`GET /api/users?role=DRIVER&active=true`** → `200`

```json
[
  {
    "id": "u_6543a1f0", "name": "Sara Ben Ali", "email": "sara@exemple.tn",
    "role": "DRIVER", "active": true, "created_at": "2026-11-18T09:00:00Z",
    "permissions": [ { "id": "perm_11", "type": "accessible", "start": "2026-11-18", "end": null } ]
  }
]
```

Le mot de passe (même haché) n'est jamais renvoyé.

**`POST /api/users`**

```json
{ "name": "Agent Accueil", "email": "agent@exemple.tn", "password": "motdepasse8", "role": "OPERATOR" }
```

→ `201` avec l'utilisateur. Erreurs : `409 email_already_exists`, `400 invalid_request`.

**`PATCH /api/users/{id}`**

```json
{ "active": false }
```

Champs possibles : `name`, `role`, `active`. → `200`. Un compte désactivé ne peut plus se connecter, et ses jetons en cours sont refusés. Erreur : `409 action_not_allowed` (un exploitant ne peut pas se désactiver lui-même ni retirer son propre rôle).

**`POST /api/users/{id}/permissions`**

```json
{ "type": "accessible", "end": "2027-06-30" }
```

`type` : `accessible` ou `electric` ; `end` facultatif (`null` = sans fin). → `201` avec l'autorisation. Erreur : `409 permission_already_exists`.

**`DELETE /api/users/{id}/permissions/{pid}`** → `204`.

### 2.12 Statistiques

**`GET /api/stats?from=2026-11-20&to=2026-11-26&granularity=day`** · OPERATOR → `200`

`granularity` : `hour` ou `day`. Par défaut, les 7 derniers jours, par jour.

```json
{
  "from": "2026-11-20", "to": "2026-11-26", "granularity": "day",
  "average_occupancy_rate": 0.63,
  "average_duration_min": 47,
  "occupancy": [ { "start": "2026-11-20T00:00:00Z", "rate": 0.58 }, { "start": "2026-11-21T00:00:00Z", "rate": 0.66 } ],
  "peak_hours": [ { "hour": 10, "rate": 0.91 }, { "hour": 17, "rate": 0.86 } ],
  "per_spot": [ { "spot": "A1", "rate": 0.32, "average_duration_min": 38 } ],
  "violations": { "straddling": 4, "overstay": 2, "unauthorized_special_spot": 1, "forbidden_zone": 3 }
}
```

Agrégations MongoDB sur `occupancy_history` et `events`. Aucune donnée personnelle dans la réponse.
Erreur : `400 invalid_request` (`from` après `to`, période de plus de 92 jours).

### 2.13 Notifications push

Routes définies par Eya en **1.5**, reprises sans changement : `GET /api/push/public-key`, `POST /api/push/subscriptions` (`201` à la création, `200` si l'`endpoint` existe déjà), `DELETE /api/push/subscriptions` (`204`). Contenu des messages : section 3.

### 2.14 Collections MongoDB

Base `smartparking`. Les champs `_id` en texte (`"A1"`, `"P1"`) sont choisis par nous ; les autres sont des `ObjectId` affichés `u_…`, `r_…` dans l'API.

| Collection | Champs | Index |
|---|---|---|
| `parkings` | `_id` (`"P1"`), `name`, `resolution` `[l, h]`, `dimensions_cm` `[l, h]`, `graph` `{nodes, edges}` (4.3), `forbidden_zones` `[{id, polygon, rect_cm}]`, `settings` (2.10), `pi` `{online, fps, cpu_temp, received_at}`, `updated_at` | — |
| `spots` | `_id` (`"A1"`), `parking`, `type`, `max_duration_min`, `polygon` (pixels, 5.2), `rect_cm` (plan, 4.3), `node`, `state` (`free`/`occupied`, venant du Pi), `since`, `lock` `{user, until, confirmed_at}` ou `null` | `parking` |
| `users` | `_id`, `name`, `email`, `password_hash` (Argon2id), `role`, `active`, `created_at` | `email` unique |
| `permissions` | `_id`, `user`, `type` (`accessible`/`electric`), `start`, `end`, `granted_by` | `user` + `type` |
| `reservations` | `_id`, `user`, `spot`, `state`, `created_at`, `expires_at`, `start`, `end`, `amount`, `currency`, `payment` `{method, paid_at, receipt}` | `user` + `created_at`, `spot` + `state` |
| `events` | `_id`, `parking`, `type`, `spots`, `zone`, `start`, `end`, `source` (`pi`/`server`), `details`, `handled`, `handled_by`, `handled_at`, `comment` | `handled` + `start`, `type` + `start` |
| `push_subscriptions` | `_id`, `user`, `endpoint`, `keys` `{p256dh, auth}`, `created_at` | `endpoint` unique, `user` |
| `occupancy_history` | `_id`, `spot`, `start`, `end`, `duration_min` (un document par stationnement terminé, pour `/stats`) | `spot` + `start` |

Aucune collection ne contient d'image, de plaque ou de visage (privacy by design) : uniquement des états, des horaires et des comptes.

---

## 3. Contrat des notifications push

Rédaction : Maissa (serveur) · Statut : proposition, à relire par Eya

Les routes et le format général sont ceux de **1.5** (Eya). Cette section fixe ce que le **serveur** envoie, type par type.

### 3.1 Charge utile

```json
{
  "type": "reservation_confirmed",
  "title": "Réservation confirmée",
  "body": "La place B2 vous est réservée jusqu'à 10:13.",
  "url": "/conducteur#reservations",
  "ts": "2026-11-20T09:58:40Z",
  "id": "r_7c21-confirmee",
  "data": { "reservation": "r_7c21", "spot": "B2" }
}
```

| Champ | Obligatoire | Contenu |
|---|---|---|
| `type` | oui | Un des types de 3.2 |
| `title` | oui | Titre court (40 caractères au plus) |
| `body` | oui | Le message affiché (120 caractères au plus) |
| `url` | oui | Chemin relatif dans la PWA, ouvert au clic |
| `ts` | oui | Date de l'événement, ISO 8601 UTC |
| `id` | oui | Identifiant unique : une notification avec le même `id` remplace la précédente |
| `data` | non | Données utiles à la page ouverte |

> Le champ du texte s'appelle **`body`** (nom choisi par Eya, déjà utilisé dans la PWA) ; c'est le « message » de la notification.

### 3.2 Types, destinataires et textes

| `type` | Destinataire | Déclenché quand | `title` / `body` (exemple) | `url` | `data` |
|---|---|---|---|---|---|
| `reservation_confirmed` | conducteur concerné | `POST /api/reservations` réussi | « Réservation confirmée » / « La place B2 vous est réservée jusqu'à 10:13. » | `/conducteur#reservations` | `reservation`, `spot` |
| `time_almost_up` | conducteur concerné | 10 min avant la fin de la durée maximale | « Fin de stationnement proche » / « Il vous reste 10 minutes sur la place B2. » | `/conducteur#reservations` | `reservation`, `spot`, `expected_end` |
| `spot_overdue` | conducteur concerné | durée maximale dépassée | « Place à libérer » / « Votre durée sur la place B2 est dépassée. » | `/conducteur#reservations` | `reservation`, `spot` |
| `spot_taken` | conducteur guidé | sa place verrouillée devient occupée sans confirmation d'arrivée dans les `arrival_confirmation_s` secondes (2.7) | « Place prise » / « A2 a été prise. Nouvelle place : A3. » | `/conducteur#guidage` | `previous_spot`, `spot`, `path` |
| `straddling` | tous les exploitants | événement `straddling` reçu du Pi | « Voiture à cheval » / « Une voiture occupe les places A3 et A4. » | `/exploitant#alertes` | `event`, `spots` |
| `overstay` | tous les exploitants | durée maximale dépassée | « Véhicule ventouse » / « A2 occupée depuis 2 h 01 (max 2 h). » | `/exploitant#alertes` | `event`, `spots`, `duration_min` |
| `unauthorized_special_spot` | tous les exploitants | place PMR ou électrique occupée sans autorisation | « Place spéciale » / « La place PMR A1 est occupée sans autorisation. » | `/exploitant#alertes` | `event`, `spots`, `spot_type` |
| `forbidden_zone` | tous les exploitants | événement `forbidden_zone` reçu du Pi | « Zone interdite » / « Un véhicule est arrêté dans allee1. » | `/exploitant#alertes` | `event`, `zone` |

Pour les quatre anomalies, `id` = l'identifiant de l'événement (`evt_…`).

### 3.3 Règles d'envoi (serveur)

- **Une notification par événement**, jamais une par image analysée.
- Réponses aux questions de 1.8 : le conducteur **ne reçoit pas** `overstay` (il reçoit `spot_overdue`) ; le préavis `time_almost_up` est de **10 minutes** (paramètre `end_warning_min`).
- Anomalies : `Urgency: high`, `TTL` 1 heure. Messages conducteur : `Urgency: normal`, `TTL` 15 minutes (un message périmé n'a plus d'intérêt).
- Si le service de push répond `404` ou `410`, l'abonnement est supprimé (1.5).
- Clés VAPID dans les variables d'environnement `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY` et `VAPID_SUBJECT` (sujet `mailto:` de l'équipe, question 3 de 1.8 : adresse à choisir ensemble). Jamais dans Git.
- Pas de donnée personnelle dans le texte : ni nom, ni email, seulement des numéros de place.

---

## 4. Contrat plan de la maquette

Rédaction : Maissa, à valider par Amal (IoT) · Statut : proposition

### 4.1 Plan

Maquette de **45 × 30 cm**, 8 places en deux rangées de part et d'autre d'une allée centrale. On entre à gauche et on sort à droite. Repère : origine en haut à gauche, `x` vers la droite, `y` vers le bas, en centimètres (même sens que le SVG de la PWA). Schéma à l'échelle (1 caractère = 1 cm en largeur) :

```text
x (cm)  0         10    16    22    28    34 37      45
        +---------+-----+-----+-----+-----+----------+  y=0
        |         | A1  | A2  | A3  | A4  |          |
        |         |(PMR)|     |     |     |          |
        |         |  o  |  o  |  o  |  o  |          |
        +---------+--|--+--|--+--|--+--|--+--+-------+  y=10
ENTRÉE  E============I1====I2====I3====I4====|#######S  SORTIE
        |            |     |     |     |     |allee1#|
        +---------+--|--+--|--+--|--+--|--+--+-------+  y=20
        |         |  o  |  o  |  o  |  o  |          |
        |         | B1  | B2  | B3  | B4  |          |
        |         |     |     |     |(ÉLEC|          |
        +---------+-----+-----+-----+-----+----------+  y=30
```

Légende : `E` entrée, `S` sortie, `I1`…`I4` intersections de l'allée, `o` centre d'une place (nœud du graphe), `=` allée centrale, `#` zone interdite `allee1`.

- **Places** : 6 cm × 10 cm, pour des voitures miniatures au 1/64 (environ 7 × 3 cm : Hot Wheels, Majorette). Il reste environ 1,5 cm de chaque côté et 3 cm en longueur, et une voiture peut se garer à cheval sur deux places.
- **A1 = PMR** (la plus proche de l'entrée, côté rangée A), **B4 = électrique**, les autres sont normales.
- **Allée centrale** : de `y = 10` à `y = 20` (10 cm de large).
- **Zone interdite `allee1`** : la fin de l'allée devant la sortie, `x` de 37 à 45, `y` de 10 à 20 (une voiture de 7 cm y tient en entier). On peut y rouler, pas s'y arrêter.
- Les bandes `x` de 0 à 10 et de 34 à 45 au-dessus et au-dessous de l'allée sont des trottoirs (non stationnables).
- Toutes les cotes sont des **centimètres entiers** : la maquette se trace à la règle, sans demi-centimètre.

| Place | Type | `max_duration_min` | Rectangle `[x, y, largeur, hauteur]` (cm) | Nœud |
|---|---|---|---|---|
| A1 | pmr | 120 | `[10, 0, 6, 10]` | `A1` |
| A2 | normale | 120 | `[16, 0, 6, 10]` | `A2` |
| A3 | normale | 120 | `[22, 0, 6, 10]` | `A3` |
| A4 | normale | 120 | `[28, 0, 6, 10]` | `A4` |
| B1 | normale | 120 | `[10, 20, 6, 10]` | `B1` |
| B2 | normale | 120 | `[16, 20, 6, 10]` | `B2` |
| B3 | normale | 120 | `[22, 20, 6, 10]` | `B3` |
| B4 | electrique | 60 | `[28, 20, 6, 10]` | `B4` |

Pour la démo, l'exploitant pourra baisser `max_duration_min` à 2 minutes afin de montrer une alerte `overstay` sans attendre.

### 4.2 Graphe de guidage

Nœuds (coordonnées en cm sur le plan) :

| Nœud | Type | `x` | `y` | Rôle |
|---|---|---|---|---|
| `E` | entree | 0 | 15 | Entrée, départ du guidage |
| `I1` | intersection | 13 | 15 | Devant A1 et B1 |
| `I2` | intersection | 19 | 15 | Devant A2 et B2 |
| `I3` | intersection | 25 | 15 | Devant A3 et B3 |
| `I4` | intersection | 31 | 15 | Devant A4 et B4 |
| `S` | sortie | 45 | 15 | Sortie |
| `A1`…`A4` | place | 13, 19, 25, 31 | 5 | Centre de la place |
| `B1`…`B4` | place | 13, 19, 25, 31 | 25 | Centre de la place |

Arêtes (non orientées, longueur en cm) :

| Arête | Longueur | Arête | Longueur |
|---|---|---|---|
| `E` – `I1` | 13 | `I1` – `A1`, `I1` – `B1` | 10 |
| `I1` – `I2` | 6 | `I2` – `A2`, `I2` – `B2` | 10 |
| `I2` – `I3` | 6 | `I3` – `A3`, `I3` – `B3` | 10 |
| `I3` – `I4` | 6 | `I4` – `A4`, `I4` – `B4` | 10 |
| `I4` – `S` | 14 | | |

Distances depuis `E` : A1 et B1 = 23 cm ; A2 et B2 = 29 ; A3 et B3 = 35 ; A4 et B4 = 41. À égalité, la règle de 2.7 choisit la rangée A. Exemple : tout est libre et le conducteur n'a pas d'autorisation PMR → A1 est exclue, B1 est proposée (trajet `E → I1 → B1`).

### 4.3 Format JSON (`GET /api/parking`, `GET` et `PUT /api/config/layout`)

```json
{
  "parking": "P1",
  "name": "Maquette SUP'COM",
  "dimensions_cm": [45, 30],
  "spots": [
    { "id": "A1", "type": "accessible", "rect_cm": [10, 0, 6, 10],  "node": "A1" },
    { "id": "A2", "type": "standard",   "rect_cm": [16, 0, 6, 10],  "node": "A2" },
    { "id": "A3", "type": "standard",   "rect_cm": [22, 0, 6, 10],  "node": "A3" },
    { "id": "A4", "type": "standard",   "rect_cm": [28, 0, 6, 10],  "node": "A4" },
    { "id": "B1", "type": "standard",   "rect_cm": [10, 20, 6, 10], "node": "B1" },
    { "id": "B2", "type": "standard",   "rect_cm": [16, 20, 6, 10], "node": "B2" },
    { "id": "B3", "type": "standard",   "rect_cm": [22, 20, 6, 10], "node": "B3" },
    { "id": "B4", "type": "electric",   "rect_cm": [28, 20, 6, 10], "node": "B4" }
  ],
  "forbidden_zones": [ { "id": "allee1", "rect_cm": [37, 10, 8, 10] } ],
  "graph": {
    "nodes": [
      { "id": "E",  "type": "entrance",     "x": 0,  "y": 15 },
      { "id": "I1", "type": "intersection", "x": 13, "y": 15 },
      { "id": "I2", "type": "intersection", "x": 19, "y": 15 },
      { "id": "I3", "type": "intersection", "x": 25, "y": 15 },
      { "id": "I4", "type": "intersection", "x": 31, "y": 15 },
      { "id": "S",  "type": "exit",         "x": 45, "y": 15 },
      { "id": "A1", "type": "spot", "x": 13, "y": 5 },  { "id": "B1", "type": "spot", "x": 13, "y": 25 },
      { "id": "A2", "type": "spot", "x": 19, "y": 5 },  { "id": "B2", "type": "spot", "x": 19, "y": 25 },
      { "id": "A3", "type": "spot", "x": 25, "y": 5 },  { "id": "B3", "type": "spot", "x": 25, "y": 25 },
      { "id": "A4", "type": "spot", "x": 31, "y": 5 },  { "id": "B4", "type": "spot", "x": 31, "y": 25 }
    ],
    "edges": [
      { "from": "E",  "to": "I1", "length_cm": 13 },
      { "from": "I1", "to": "I2", "length_cm": 6 },
      { "from": "I2", "to": "I3", "length_cm": 6 },
      { "from": "I3", "to": "I4", "length_cm": 6 },
      { "from": "I4", "to": "S",  "length_cm": 14 },
      { "from": "I1", "to": "A1", "length_cm": 10 }, { "from": "I1", "to": "B1", "length_cm": 10 },
      { "from": "I2", "to": "A2", "length_cm": 10 }, { "from": "I2", "to": "B2", "length_cm": 10 },
      { "from": "I3", "to": "A3", "length_cm": 10 }, { "from": "I3", "to": "B3", "length_cm": 10 },
      { "from": "I4", "to": "A4", "length_cm": 10 }, { "from": "I4", "to": "B4", "length_cm": 10 }
    ]
  }
}
```

Deux repères différents, reliés par l'identifiant des places et des zones :
- **centimètres** (`rect_cm`, graphe) : pour le plan SVG et le guidage ;
- **pixels de la caméra** (`polygon`, section 5.2) : pour la détection sur le Pi.

### 4.4 Recommandations physiques

| Élément | Recommandation | Pourquoi |
|---|---|---|
| Fond | Carton ou mousse **mat, gris moyen** ; pas de surface brillante. Ou le fond imprimé de [`iot/impression/`](../iot/impression/generer_fond.py) (51 × 36 cm avec la marge, papier mat) | Contraste avec les voitures, pas de reflets |
| Marquage | Lignes **blanches mates** de 6 mm (ruban ou impression), sur les bords des places ; zone interdite hachurée | Repères visibles pour dessiner les polygones |
| Voitures | 6 à 10 voitures au **1/64** (environ 7 cm) de **couleurs vives** (rouge, bleu, jaune, blanc…) ; **pas de voiture noire** ni gris foncé | Le test S1 a montré des oublis sur les voitures sombres |
| Caméra | **Fixée** sur un support rigide, objectif **perpendiculaire** au plan, à 45–55 cm de haut environ (à ajuster pour voir toute la maquette) | Une caméra qui bouge oblige à redessiner tous les polygones |
| Cadrage | **Toutes les places entièrement visibles**, avec au moins 3 cm de marge autour de la maquette | Le test S1 a montré des oublis sur les voitures coupées au bord |
| Éclairage | **LED constante et diffuse** (deux sources de chaque côté), pas de lumière du jour directe ni d'ombre portée de la caméra | Même image le matin et le soir, pas de fausses détections |
| Résolution | 640 × 480 pour la détection (environ 12 pixels par cm) | Suffisant pour YOLO11n et rapide sur le Pi |

Après toute modification de la caméra ou de la maquette : nouvelle photo de référence, puis nouveau dessin des polygones (`PUT /api/config/polygons`).

---

## 5. MQTT et polygones

Rédaction : Amal (IoT) · Statut : **validé**, repris de [`iot/README.md`](../iot/README.md). Les ajouts proposés sont en 5.3, à part.

### 5.1 Contrat MQTT

| Topic | Exemple | Envoyé quand |
|---|---|---|
| `parking/P1/spots/<id>` | `{"spot":"A3","state":"occupied","since":"2026-11-20T10:02:11Z"}` | changement d'état (retain, QoS 1) |
| `parking/P1/events` | `{"type":"straddling","spots":["A3","A4"],"ts":"..."}` | début d'une anomalie |
| `parking/P1/events` | `{"type":"forbidden_zone","zone":"allee1","ts":"..."}` | début d'une anomalie |
| `parking/P1/pi/status` | `{"online":true,"fps":0.8,"cpu_temp":61}` | toutes les 60 s |

- `state` vaut `free` ou `occupied`.
- Le serveur calcule la règle « ventouse » à partir de `since`.
- Broker Mosquitto en **TLS sur le port 8883**.

### 5.2 Contrat des polygones (`places.json`)

| Champ | Contenu |
|---|---|
| `parking` | Identifiant du parking (`"P1"`) |
| `resolution` | `[largeur, hauteur]` de l'image de la caméra, en pixels |
| `spots` | Liste ; chaque place a `id`, `type` (`standard`, `accessible`, `electric`), `max_duration_min`, `polygon` = liste de points `[x, y]` en pixels |
| `forbidden_zones` | Liste ; chaque zone a `id` et `polygon` |

Modèle : [`iot/places.exemple.json`](../iot/places.exemple.json) (4 places, exemple de format). Le vrai `places.json` de la maquette aura les 8 places et la zone `allee1` de la section 4, avec les types de 4.1.

### 5.3 Propositions d'ajout (à valider par Amal)

Ces ajouts ne changent rien aux messages de 5.1 ; ils complètent le contrat pour la configuration et la sécurité du broker.

| Topic | Sens | Exemple | Envoyé quand |
|---|---|---|---|
| `parking/P1/config` | serveur → Pi | le fichier `places.json` complet (5.2) | à chaque `PUT /api/config/polygons` ou `PATCH /api/config/spots/{id}` (retain, QoS 1) |
| `parking/P1/pi/status` | Pi → serveur | `{"online":false}` | **dernière volonté** (LWT, retain) : publiée par le broker si le Pi se déconnecte brutalement |

Comptes du broker (mot de passe obligatoire, pas de connexion anonyme) :

| Compte | Publie sur | S'abonne à |
|---|---|---|
| `pi` | `parking/P1/spots/#`, `parking/P1/events`, `parking/P1/pi/status` | `parking/P1/config` |
| `server` | `parking/P1/config` | `parking/P1/#` |