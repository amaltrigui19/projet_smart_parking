# Contrats d'interface

Les trois parties du projet (IoT, serveur, PWA) sont développées en parallèle par des personnes différentes. Un **contrat** fixe ce qu'une partie envoie ou attend d'une autre : tant qu'il est respecté, chacune avance sans attendre les autres, avec des données simulées si besoin.

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
- Erreurs : code HTTP adapté (400, 401, 403, 404, 409) et corps `{"erreur": "code_court", "message": "texte lisible"}`. La PWA affiche `message` à l'utilisateur.
- Noms d'anomalie identiques partout (MQTT, API, push) : `cheval`, `ventouse`, `place_speciale`, `zone_interdite`.

### 1.2 Authentification

- La PWA envoie `Authorization: Bearer <jwt>` sur chaque appel.
- Le JWT contient l'identifiant de l'utilisateur (`sub`), son rôle (`role` : `CONDUCTEUR` ou `EXPLOITANT`) et une expiration (`exp`). La PWA lit le `role` pour choisir l'interface à afficher ; **la sécurité reste côté serveur**, qui vérifie le rôle à chaque appel.
- Si l'API répond `401`, la PWA efface le jeton et renvoie vers la page de connexion. Si elle répond `403`, elle affiche « accès refusé ».
- Le jeton est gardé dans `sessionStorage` (pas dans `localStorage`) ; il n'est jamais mis dans une URL.

### 1.3 Ce dont la PWA a besoin de l'API

| Écran | Besoin | Rôle |
|---|---|---|
| Inscription, connexion | Créer un compte, obtenir un JWT | public |
| Plan du parking (les deux) | Liste des places avec leur état (`libre`, `occupee`, `reservee`), type (`normale`, `pmr`, `electrique`) et polygone | tous |
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

- Connexion : `/api/ws/places?token=<jwt>` (un navigateur ne peut pas mettre d'en-tête `Authorization` sur un WebSocket). Le serveur refuse la connexion sans JWT valide.
- Message reçu à chaque changement d'état d'une place :

```json
{ "place": "A3", "etat": "occupee", "depuis": "2026-11-20T10:02:11Z" }
```

- À la connexion, la PWA charge d'abord l'état complet par l'API REST, puis applique les messages WebSocket. Si le WebSocket se coupe, elle se reconnecte toute seule et recharge l'état complet.

### 1.5 Notifications push

Web Push avec clés VAPID. La clé **privée** reste dans une variable d'environnement sur la VM, **jamais dans Git**.

**Routes à fournir par le serveur :**

| Route | Rôle | Description |
|---|---|---|
| `GET /api/push/cle-publique` | public | Renvoie `{"cle": "<clé publique VAPID en base64url>"}` |
| `POST /api/push/abonnements` | connecté | Le corps est l'objet `PushSubscription` du navigateur, tel quel (`endpoint`, `keys.p256dh`, `keys.auth`). `201` à la création, `200` si l'`endpoint` existe déjà (appel idempotent). |
| `DELETE /api/push/abonnements` | connecté | Corps `{"endpoint": "..."}`, réponse `204`. |

Un utilisateur peut avoir plusieurs abonnements. Si le service de push répond `404` ou `410` à un envoi, le serveur supprime l'abonnement.

**Message envoyé à la PWA** (4 Ko maximum) :

```json
{
  "type": "cheval",
  "titre": "Voiture à cheval",
  "corps": "Une voiture occupe les places A3 et A4.",
  "url": "/exploitant#alertes",
  "ts": "2026-11-20T10:02:11Z",
  "id": "evt_8f3a",
  "donnees": { "places": ["A3", "A4"] }
}
```

`type`, `titre`, `corps`, `url` (chemin relatif dans la PWA), `ts` et `id` sont obligatoires ; `id` sert à ne pas afficher deux fois la même notification.

| `type` | Destinataire | Déclenché quand |
|---|---|---|
| `reservation_confirmee` | Conducteur | La réservation est créée |
| `fin_duree` | Conducteur | Il reste peu de temps avant la fin de la durée autorisée |
| `place_a_liberer` | Conducteur | La durée autorisée est dépassée |
| `place_prise` | Conducteur | La place proposée par le guidage a été prise, le trajet est recalculé |
| `cheval` | Exploitant | Une voiture est à cheval sur deux places |
| `ventouse` | Exploitant | Durée autorisée dépassée ou véhicule présent plusieurs jours |
| `place_speciale` | Exploitant | Place PMR, électrique ou réservée occupée sans autorisation |
| `zone_interdite` | Exploitant | Un véhicule est dans une zone interdite |

Les noms `cheval` et `zone_interdite` sont ceux du contrat MQTT ; `ventouse` et `place_speciale` sont calculés par le serveur.

**Côté PWA** (service worker) : à la réception, affichage de la notification avec `titre` et `corps` (un même `id` remplace la précédente) ; au clic, ouverture ou mise au premier plan de la PWA sur `url`. La permission n'est demandée qu'après une action de l'utilisateur (bouton « Activer les notifications »). Sur iPhone, la PWA doit d'abord être ajoutée à l'écran d'accueil.

### 1.6 Outil de calibration (polygones)

La PWA fournit l'outil qui dessine les places sur une photo de la maquette. Il produit un fichier au format de `iot/places.exemple.json` (`parking`, `resolution`, `places[id, type, duree_max_min, poly]`, `zones_interdites`), coordonnées **en pixels de l'image de la caméra**. Le serveur l'enregistre et le Pi le lit : le format est donc celui de la section 5.

### 1.7 Ce que la PWA attend de la maquette (section 4)

Pour dessiner le plan SVG et le trajet : la **liste des places** avec leur numéro, la **résolution de l'image** de la caméra et le **graphe** du parking (nœuds, coordonnées sur le plan, arêtes). Sans graphe, le trajet n'est pas affichable.

### 1.8 Questions ouvertes

1. Le conducteur reçoit-il `ventouse` quand c'est son véhicule ? (proposition : non, seulement `place_a_liberer`)
2. Délai de `fin_duree` : 10 minutes avant la fin ? (proposition : oui)
3. Adresse d'équipe pour le sujet VAPID (`mailto:`) : laquelle ?
4. L'état `reservee` d'une place : porté par l'API des places, ou à déduire des réservations ?

---

## 2. Contrat API REST et base MongoDB

Rédaction : Maissa (serveur) · Statut : proposition, à relire par Eya (PWA) et Amal (IoT)

Cette section répond aux besoins listés en 1.3 à 1.5. Les noms des routes sont en français, comme celles d'Eya (`/api/push/cle-publique`) ; ils remplacent les noms provisoires de `repartition_travail.md` (`/auth/register` devient `/api/auth/inscription`, etc.).

### 2.1 Conventions

- **URL de base** : `https://smartparking-supcom.duckdns.org/api` (VM AWS ; l'API y sera déployée au sprint 2) et `https://localhost/api` en local (`infra/`, voir `docs/installation-serveur.md`).
- Préfixe **`/api`**, JSON en UTF-8, dates ISO 8601 UTC avec `Z` (voir 1.1).
- Noms des champs en `snake_case`, sans accents (`duree_max_min`, `mot_de_passe`).
- Un seul parking dans le projet : `P1`. Les routes ne le répètent pas ; il figure dans les réponses quand c'est utile.
- Identifiants : places `A1`…`B4`, nœuds du graphe `E`, `I1`…, `S` (section 4), utilisateurs `u_…`, réservations `r_…`, événements `evt_…`, autorisations `aut_…`.
- **Erreurs** : toujours `{"erreur": "code_court", "message": "texte lisible"}`. Codes communs à toutes les routes :

| HTTP | `erreur` | Quand |
|---|---|---|
| 400 | `requete_invalide` | JSON mal formé, champ manquant ou hors limites (le `message` dit lequel) |
| 401 | `non_authentifie` | JWT absent, invalide ou expiré |
| 403 | `acces_refuse` | Le rôle ne permet pas cette route |
| 404 | `introuvable` | L'objet demandé n'existe pas |
| 500 | `erreur_serveur` | Erreur interne (détail dans les journaux du serveur, jamais dans la réponse) |

Les codes propres à chaque route (souvent `409`) sont donnés avec la route.

### 2.2 Authentification et JWT

- En-tête sur chaque appel protégé : `Authorization: Bearer <jwt>`.
- JWT signé **RS256** (format MicroProfile JWT, pris en charge par WildFly). La clé privée est dans une variable d'environnement de la VM, jamais dans Git.
- Contenu du jeton :

```json
{
  "iss": "smartparking",
  "sub": "u_6543a1f0",
  "upn": "sara@exemple.tn",
  "role": "CONDUCTEUR",
  "groups": ["CONDUCTEUR"],
  "iat": 1795168800,
  "exp": 1795172400
}
```

`role` est lu par la PWA (1.2) ; `groups` est lu par WildFly pour `@RolesAllowed`. Durée de vie : **1 heure**. Pas de jeton de rafraîchissement au sprint 3 : à l'expiration, l'utilisateur se reconnecte.

Colonne « Rôle » des tableaux : **public** = sans jeton ; **connecté** = `CONDUCTEUR` ou `EXPLOITANT` ; sinon le rôle exigé.

### 2.3 Vue d'ensemble des routes

| Méthode | Chemin | Rôle | Objet | Sprint |
|---|---|---|---|---|
| GET | `/api/sante` | public | Le serveur répond (test de déploiement) | 1 |
| POST | `/api/auth/inscription` | public | Créer un compte conducteur | 3 |
| POST | `/api/auth/connexion` | public | Obtenir un JWT | 3 |
| GET | `/api/auth/moi` | connecté | Mon profil et mes autorisations | 3 |
| GET | `/api/parking` | connecté | Plan : dimensions, places, zones, graphe (section 4) | 4 |
| GET | `/api/places` | connecté | Toutes les places avec leur état | 4 |
| GET | `/api/places/{id}` | connecté | Détail d'une place | 4 |
| WS | `/api/ws/places?token=<jwt>` | connecté | Changements d'état en temps réel (1.4) | 4 |
| POST | `/api/guidage` | CONDUCTEUR | Place libre la plus proche, trajet et verrouillage | 7–8 |
| GET | `/api/guidage` | CONDUCTEUR | Mon guidage en cours | 8 |
| DELETE | `/api/guidage` | CONDUCTEUR | Abandonner le guidage (libère le verrou) | 8 |
| POST | `/api/reservations` | CONDUCTEUR | Réserver une place | 6 |
| GET | `/api/reservations` | connecté | Mon historique (exploitant : toutes) | 6–7 |
| GET | `/api/reservations/{id}` | connecté | Détail d'une réservation | 6 |
| DELETE | `/api/reservations/{id}` | CONDUCTEUR | Annuler ma réservation | 6 |
| POST | `/api/reservations/{id}/paiement` | CONDUCTEUR | Payer (simulé) | 7 |
| GET | `/api/tableau-de-bord` | EXPLOITANT | Résumé, alertes non traitées, statut du Pi | 5 |
| GET | `/api/evenements` | EXPLOITANT | Liste des anomalies | 8 |
| PATCH | `/api/evenements/{id}` | EXPLOITANT | Marquer comme traitée | 8 |
| GET, PUT | `/api/config/polygones` | EXPLOITANT | Fichier `places.json` (section 5.2) | 3 |
| GET, PUT | `/api/config/plan` | EXPLOITANT | Plan et graphe (section 4) | 6 |
| PATCH | `/api/config/places/{id}` | EXPLOITANT | Type et durée maximale d'une place | 6 |
| GET, PATCH | `/api/config/parametres` | EXPLOITANT | Tarif et délais | 6 |
| GET | `/api/utilisateurs` | EXPLOITANT | Liste des comptes | 6 |
| POST | `/api/utilisateurs` | EXPLOITANT | Créer un compte (dont exploitant) | 6 |
| PATCH | `/api/utilisateurs/{id}` | EXPLOITANT | Modifier, désactiver | 6 |
| POST | `/api/utilisateurs/{id}/autorisations` | EXPLOITANT | Donner une autorisation PMR ou électrique | 6 |
| DELETE | `/api/utilisateurs/{id}/autorisations/{aid}` | EXPLOITANT | Retirer une autorisation | 6 |
| GET | `/api/stats` | EXPLOITANT | Statistiques | 9 |
| GET | `/api/push/cle-publique` | public | Clé publique VAPID (1.5) | 9 |
| POST | `/api/push/abonnements` | connecté | Enregistrer un abonnement (1.5) | 9 |
| DELETE | `/api/push/abonnements` | connecté | Supprimer un abonnement (1.5) | 9 |

### 2.4 Santé

**`GET /api/sante`** · public → `200`

```json
{ "statut": "ok", "version": "0.1.0", "mongo": "ok", "mqtt": "ok", "ts": "2026-11-06T14:00:00Z" }
```

`mongo` et `mqtt` valent `ok` ou `ko`. Si l'un des deux vaut `ko`, la réponse est `503` avec le même corps. C'est la page de test du déploiement (sprint 1).

### 2.5 Authentification

**`POST /api/auth/inscription`** · public

```json
{ "nom": "Sara Ben Ali", "email": "sara@exemple.tn", "mot_de_passe": "motdepasse8" }
```

→ `201`

```json
{ "id": "u_6543a1f0", "nom": "Sara Ben Ali", "email": "sara@exemple.tn", "role": "CONDUCTEUR", "actif": true }
```

Erreurs : `400 requete_invalide` (email invalide, mot de passe de moins de 8 caractères), `409 email_existant`.
Le rôle est toujours `CONDUCTEUR` : un champ `role` envoyé ici est ignoré.

**`POST /api/auth/connexion`** · public

```json
{ "email": "sara@exemple.tn", "mot_de_passe": "motdepasse8" }
```

→ `200`

```json
{
  "jeton": "eyJhbGciOiJSUzI1NiIs...",
  "type": "Bearer",
  "expire_dans": 3600,
  "utilisateur": { "id": "u_6543a1f0", "nom": "Sara Ben Ali", "email": "sara@exemple.tn", "role": "CONDUCTEUR" }
}
```

Erreurs : `401 identifiants_invalides` (même message que l'email ou le mot de passe soit faux), `403 compte_desactive`.

**`GET /api/auth/moi`** · connecté → `200`

```json
{
  "id": "u_6543a1f0", "nom": "Sara Ben Ali", "email": "sara@exemple.tn", "role": "CONDUCTEUR",
  "autorisations": [ { "id": "aut_11", "type": "pmr", "fin": null } ]
}
```

### 2.6 Places et temps réel

**`GET /api/parking`** · connecté → `200` : le JSON du plan décrit en **4.3** (dimensions, places, zones, graphe). La PWA s'en sert pour dessiner le plan SVG et le trajet.

**`GET /api/places`** · connecté → `200`

```json
{
  "parking": "P1",
  "ts": "2026-11-20T10:05:00Z",
  "resume": { "total": 8, "libres": 5, "occupees": 2, "reservees": 1 },
  "places": [
    { "id": "A1", "type": "pmr",        "etat": "libre",    "depuis": "2026-11-20T08:00:00Z", "duree_max_min": 120 },
    { "id": "A2", "type": "normale",    "etat": "occupee",  "depuis": "2026-11-20T10:02:11Z", "duree_max_min": 120 },
    { "id": "B4", "type": "electrique", "etat": "reservee", "depuis": "2026-11-20T09:58:40Z", "duree_max_min": 60 }
  ]
}
```

- `etat` : `libre`, `occupee` ou `reservee`. **Le serveur calcule `reservee`** (réponse à la question 4 de 1.8) : une place libre avec une réservation active est renvoyée `reservee`. Le Pi, lui, ne connaît que `libre` et `occupee`.
- Une place **verrouillée par le guidage** reste affichée `libre` (elle l'est réellement) ; elle n'est simplement pas proposée à un autre conducteur.

**`GET /api/places/{id}`** · connecté → `200` : les mêmes champs, plus `depassement` (booléen, durée maximale dépassée) et, pour l'exploitant seulement, `verrou` (`null` ou `{"jusqu_a": "..."}`). Erreur : `404 introuvable`.

**WebSocket `/api/ws/places?token=<jwt>`** : format défini en 1.4. Il envoie aussi l'état `reservee`.

> **Proposition d'ajout (à valider par Eya)** : les exploitants reçoivent en plus, sur le même WebSocket, des messages avec un champ `type`. Un message **sans** `type` reste un changement de place (format de 1.4, inchangé).
>
> ```json
> { "type": "evenement", "evenement": { "id": "evt_8f3a", "type": "cheval", "places": ["A3", "A4"], "debut": "2026-11-20T10:02:11Z" } }
> { "type": "pi_statut", "en_ligne": true, "fps": 0.8, "temp_cpu": 61, "recu_le": "2026-11-20T10:05:00Z" }
> ```

### 2.7 Guidage

**`POST /api/guidage`** · CONDUCTEUR

```json
{ "depart": "E" }
```

Le corps est facultatif (`depart` vaut `E`, l'entrée, par défaut).

→ `200`

```json
{
  "place": "A2",
  "distance_cm": 39.5,
  "trajet": ["E", "I1", "I2", "A2"],
  "verrou_jusqu_a": "2026-11-20T10:08:00Z"
}
```

Règles :
- Plus court chemin (Dijkstra) dans le graphe de la section 4, vers les places `libre`, non verrouillées par un autre conducteur et non réservées.
- Places `pmr` ou `electrique` : proposées seulement si le conducteur a l'autorisation correspondante, valide.
- À égalité de distance, l'identifiant le plus petit dans l'ordre alphabétique gagne (`A1` avant `B1`).
- La place est **verrouillée 3 minutes** (paramètre `verrou_min`). Un conducteur n'a qu'un verrou : un nouvel appel pendant le verrou renvoie la même place.
- Le verrou disparaît quand la place devient `occupee` (le conducteur est arrivé), à l'expiration ou par `DELETE /api/guidage`.
- Si la place est occupée par quelqu'un d'autre pendant le verrou, le serveur calcule un nouveau trajet et envoie la notification `place_prise` (section 3) avec la nouvelle place.

Erreurs : `409 parking_complet` (aucune place possible pour ce conducteur), `400 requete_invalide` (`depart` inconnu).

**`GET /api/guidage`** · CONDUCTEUR → `200` avec le même corps, ou `404 introuvable` s'il n'y a pas de guidage en cours.
**`DELETE /api/guidage`** · CONDUCTEUR → `204`.

### 2.8 Réservations et paiement simulé

États d'une réservation :

```
active ──(la voiture arrive : place occupee)──▶ en_cours ──(la voiture part)──▶ terminee ──(paiement)──▶ payee
  │                                                │
  ├──(annulation)──▶ annulee                       └──(paiement avant le départ)──▶ payee
  └──(15 min sans arrivée)──▶ expiree
```

**`POST /api/reservations`** · CONDUCTEUR

```json
{ "place": "B2" }
```

→ `201`

```json
{
  "id": "r_7c21", "place": "B2", "etat": "active",
  "creee_le": "2026-11-20T09:58:40Z", "expire_le": "2026-11-20T10:13:40Z",
  "debut": null, "fin": null, "montant": null, "paiement": null
}
```

Erreurs : `409 place_indisponible` (place pas libre, ou verrouillée pour un autre conducteur), `409 reservation_deja_active` (une seule réservation `active` ou `en_cours` par conducteur), `403 autorisation_requise` (place PMR ou électrique sans autorisation), `404 introuvable` (place inconnue).

**`GET /api/reservations?etat=payee&limite=20`** · connecté → `200` : liste du plus récent au plus ancien, objets au format ci-dessus. Le conducteur ne voit que les siennes ; l'exploitant voit toutes, filtrables avec `utilisateur=u_…`. Paramètres facultatifs : `etat`, `limite` (20 par défaut, 100 au plus).

**`GET /api/reservations/{id}`** · connecté → `200`. Erreur : `404 introuvable` (y compris pour la réservation d'un autre conducteur, pour ne pas révéler qu'elle existe).

**`DELETE /api/reservations/{id}`** · CONDUCTEUR → `200` avec la réservation à l'état `annulee`. Elle reste dans l'historique. Erreurs : `404 introuvable`, `409 annulation_impossible` (état autre que `active`).

**`POST /api/reservations/{id}/paiement`** · CONDUCTEUR

```json
{ "moyen": "simule" }
```

→ `200`

```json
{
  "id": "r_7c21", "place": "B2", "etat": "payee",
  "debut": "2026-11-20T10:01:05Z", "fin": "2026-11-20T11:20:30Z",
  "montant": 2.25, "devise": "TND",
  "paiement": { "moyen": "simule", "paye_le": "2026-11-20T11:21:00Z", "recu": "RECU-2026-000042" }
}
```

- Montant = `tarif_horaire` × durée arrondie au quart d'heure supérieur (1 quart d'heure au minimum). Si la voiture est encore là (`en_cours`), la durée va jusqu'au moment du paiement.
- **Paiement simulé** : aucune donnée bancaire n'est demandée ni stockée ; `moyen` vaut toujours `simule`.

Erreurs : `409 deja_payee`, `409 paiement_impossible` (état `active`, `annulee` ou `expiree`).

### 2.9 Exploitant : tableau de bord et événements

**`GET /api/tableau-de-bord`** · EXPLOITANT → `200`

```json
{
  "ts": "2026-11-20T10:05:00Z",
  "resume": { "total": 8, "libres": 5, "occupees": 2, "reservees": 1 },
  "taux_occupation": 0.375,
  "alertes_non_traitees": 2,
  "pi": { "en_ligne": true, "fps": 0.8, "temp_cpu": 61, "recu_le": "2026-11-20T10:04:12Z" }
}
```

`taux_occupation` = (occupées + réservées) / total. Le Pi est affiché `en_ligne: false` s'il n'a rien envoyé depuis 3 minutes (paramètre `pi_hors_ligne_min`).

**`GET /api/evenements?traite=false&type=cheval&du=2026-11-20&au=2026-11-21&limite=50`** · EXPLOITANT → `200` (tous les paramètres sont facultatifs ; tri du plus récent au plus ancien)

```json
[
  {
    "id": "evt_8f3a", "type": "cheval", "places": ["A3", "A4"], "zone": null,
    "debut": "2026-11-20T10:02:11Z", "fin": null, "source": "pi",
    "traite": false, "traite_par": null, "traite_le": null, "commentaire": null
  },
  {
    "id": "evt_8f40", "type": "ventouse", "places": ["A2"], "zone": null,
    "debut": "2026-11-20T12:02:11Z", "fin": null, "source": "serveur",
    "details": { "duree_min": 121, "duree_max_min": 120 },
    "traite": false, "traite_par": null, "traite_le": null, "commentaire": null
  }
]
```

- `type` : `cheval`, `zone_interdite` (envoyés par le Pi), `ventouse`, `place_speciale` (calculés par le serveur).
- `fin` est rempli quand le serveur sait que l'anomalie est finie (la place redevient libre) ; sinon `null`.

**`PATCH /api/evenements/{id}`** · EXPLOITANT

```json
{ "traite": true, "commentaire": "Conducteur prévenu" }
```

→ `200` avec l'événement mis à jour (`traite_par`, `traite_le` remplis par le serveur). Erreur : `404 introuvable`.

### 2.10 Exploitant : configuration

**`GET /api/config/polygones`** → `200` : le fichier `places.json` au format de **5.2**.
**`PUT /api/config/polygones`** : corps = ce même fichier, en entier. → `200` avec le fichier enregistré. Le serveur le publie au Pi sur `parking/P1/config` (proposition 5.3), pour que le Pi le reçoive sans redémarrage.
Erreur `400 config_invalide` si : identifiant en double, polygone de moins de 3 points ou en dehors de `resolution`, `type` inconnu, `duree_max_min` ≤ 0, place absente du plan.

**`GET /api/config/plan`** → `200` : JSON de **4.3**. **`PUT /api/config/plan`** : corps = ce JSON. Erreur `400 config_invalide` si le graphe n'est pas connexe, si une place n'a pas de nœud ou si le nœud `E` manque.

**`PATCH /api/config/places/{id}`**

```json
{ "type": "electrique", "duree_max_min": 60 }
```

→ `200` avec la place. Les deux champs sont facultatifs. Le changement est aussi republié au Pi.

**`GET /api/config/parametres`** → `200`. **`PATCH /api/config/parametres`** : un ou plusieurs champs → `200`.

```json
{
  "tarif_horaire": 1.5, "devise": "TND",
  "verrou_min": 3, "delai_reservation_min": 15,
  "preavis_fin_duree_min": 10, "pi_hors_ligne_min": 3
}
```

### 2.11 Exploitant : utilisateurs et autorisations

**`GET /api/utilisateurs?role=CONDUCTEUR&actif=true`** → `200`

```json
[
  {
    "id": "u_6543a1f0", "nom": "Sara Ben Ali", "email": "sara@exemple.tn",
    "role": "CONDUCTEUR", "actif": true, "cree_le": "2026-11-18T09:00:00Z",
    "autorisations": [ { "id": "aut_11", "type": "pmr", "debut": "2026-11-18", "fin": null } ]
  }
]
```

Le mot de passe (même haché) n'est jamais renvoyé.

**`POST /api/utilisateurs`**

```json
{ "nom": "Agent Accueil", "email": "agent@exemple.tn", "mot_de_passe": "motdepasse8", "role": "EXPLOITANT" }
```

→ `201` avec l'utilisateur. Erreurs : `409 email_existant`, `400 requete_invalide`.

**`PATCH /api/utilisateurs/{id}`**

```json
{ "actif": false }
```

Champs possibles : `nom`, `role`, `actif`. → `200`. Un compte désactivé ne peut plus se connecter, et ses jetons en cours sont refusés. Erreur : `409 action_interdite` (un exploitant ne peut pas se désactiver lui-même ni retirer son propre rôle).

**`POST /api/utilisateurs/{id}/autorisations`**

```json
{ "type": "pmr", "fin": "2027-06-30" }
```

`type` : `pmr` ou `electrique` ; `fin` facultatif (`null` = sans fin). → `201` avec l'autorisation. Erreur : `409 autorisation_existante`.

**`DELETE /api/utilisateurs/{id}/autorisations/{aid}`** → `204`.

### 2.12 Statistiques

**`GET /api/stats?du=2026-11-20&au=2026-11-26&granularite=jour`** · EXPLOITANT → `200`

`granularite` : `heure` ou `jour`. Par défaut, les 7 derniers jours, par jour.

```json
{
  "du": "2026-11-20", "au": "2026-11-26", "granularite": "jour",
  "taux_occupation_moyen": 0.63,
  "duree_moyenne_min": 47,
  "occupation": [ { "debut": "2026-11-20T00:00:00Z", "taux": 0.58 }, { "debut": "2026-11-21T00:00:00Z", "taux": 0.66 } ],
  "heures_de_pointe": [ { "heure": 10, "taux": 0.91 }, { "heure": 17, "taux": 0.86 } ],
  "par_place": [ { "place": "A1", "taux": 0.32, "duree_moyenne_min": 38 } ],
  "infractions": { "cheval": 4, "ventouse": 2, "place_speciale": 1, "zone_interdite": 3 }
}
```

Agrégations MongoDB sur `historique_occupation` et `evenements`. Aucune donnée personnelle dans la réponse.
Erreur : `400 requete_invalide` (`du` après `au`, période de plus de 92 jours).

### 2.13 Notifications push

Routes définies par Eya en **1.5**, reprises sans changement : `GET /api/push/cle-publique`, `POST /api/push/abonnements` (`201` à la création, `200` si l'`endpoint` existe déjà), `DELETE /api/push/abonnements` (`204`). Contenu des messages : section 3.

### 2.14 Collections MongoDB

Base `smartparking`. Les champs `_id` en texte (`"A1"`, `"P1"`) sont choisis par nous ; les autres sont des `ObjectId` affichés `u_…`, `r_…` dans l'API.

| Collection | Champs | Index |
|---|---|---|
| `parkings` | `_id` (`"P1"`), `nom`, `resolution` `[l, h]`, `dimensions_cm` `[l, h]`, `graphe` `{noeuds, aretes}` (4.3), `zones_interdites` `[{id, poly, rect_cm}]`, `parametres` (2.10), `pi` `{en_ligne, fps, temp_cpu, recu_le}`, `maj_le` | — |
| `places` | `_id` (`"A1"`), `parking`, `type`, `duree_max_min`, `poly` (pixels, 5.2), `rect_cm` (plan, 4.3), `noeud`, `etat` (`libre`/`occupee`, venant du Pi), `depuis`, `verrou` `{utilisateur, jusqu_a}` ou `null` | `parking` |
| `utilisateurs` | `_id`, `nom`, `email`, `mot_de_passe_hash` (PBKDF2 ou bcrypt), `role`, `actif`, `cree_le` | `email` unique |
| `autorisations` | `_id`, `utilisateur`, `type` (`pmr`/`electrique`), `debut`, `fin`, `accordee_par` | `utilisateur` + `type` |
| `reservations` | `_id`, `utilisateur`, `place`, `etat`, `creee_le`, `expire_le`, `debut`, `fin`, `montant`, `devise`, `paiement` `{moyen, paye_le, recu}` | `utilisateur` + `creee_le`, `place` + `etat` |
| `evenements` | `_id`, `parking`, `type`, `places`, `zone`, `debut`, `fin`, `source` (`pi`/`serveur`), `details`, `traite`, `traite_par`, `traite_le`, `commentaire` | `traite` + `debut`, `type` + `debut` |
| `abonnements_push` | `_id`, `utilisateur`, `endpoint`, `cles` `{p256dh, auth}`, `cree_le` | `endpoint` unique, `utilisateur` |
| `historique_occupation` | `_id`, `place`, `debut`, `fin`, `duree_min` (un document par stationnement terminé, pour `/stats`) | `place` + `debut` |

Aucune collection ne contient d'image, de plaque ou de visage (privacy by design) : uniquement des états, des horaires et des comptes.

---

## 3. Contrat des notifications push

Rédaction : Maissa (serveur) · Statut : proposition, à relire par Eya

Les routes et le format général sont ceux de **1.5** (Eya). Cette section fixe ce que le **serveur** envoie, type par type.

### 3.1 Charge utile

```json
{
  "type": "reservation_confirmee",
  "titre": "Réservation confirmée",
  "corps": "La place B2 vous est réservée jusqu'à 10:13.",
  "url": "/conducteur#reservations",
  "ts": "2026-11-20T09:58:40Z",
  "id": "r_7c21-confirmee",
  "donnees": { "reservation": "r_7c21", "place": "B2" }
}
```

| Champ | Obligatoire | Contenu |
|---|---|---|
| `type` | oui | Un des types de 3.2 |
| `titre` | oui | Titre court (40 caractères au plus) |
| `corps` | oui | Le message affiché (120 caractères au plus) |
| `url` | oui | Chemin relatif dans la PWA, ouvert au clic |
| `ts` | oui | Date de l'événement, ISO 8601 UTC |
| `id` | oui | Identifiant unique : une notification avec le même `id` remplace la précédente |
| `donnees` | non | Données utiles à la page ouverte |

> Le champ du texte s'appelle **`corps`** (nom choisi par Eya, déjà utilisé dans la PWA) ; c'est le « message » de la notification.

### 3.2 Types, destinataires et textes

| `type` | Destinataire | Déclenché quand | `titre` / `corps` (exemple) | `url` | `donnees` |
|---|---|---|---|---|---|
| `reservation_confirmee` | conducteur concerné | `POST /api/reservations` réussi | « Réservation confirmée » / « La place B2 vous est réservée jusqu'à 10:13. » | `/conducteur#reservations` | `reservation`, `place` |
| `fin_duree` | conducteur concerné | 10 min avant la fin de la durée maximale | « Fin de stationnement proche » / « Il vous reste 10 minutes sur la place B2. » | `/conducteur#reservations` | `reservation`, `place`, `fin_prevue` |
| `place_a_liberer` | conducteur concerné | durée maximale dépassée | « Place à libérer » / « Votre durée sur la place B2 est dépassée. » | `/conducteur#reservations` | `reservation`, `place` |
| `place_prise` | conducteur guidé | sa place verrouillée est prise | « Place prise » / « A2 a été prise. Nouvelle place : A3. » | `/conducteur#guidage` | `ancienne_place`, `place`, `trajet` |
| `cheval` | tous les exploitants | événement `cheval` reçu du Pi | « Voiture à cheval » / « Une voiture occupe les places A3 et A4. » | `/exploitant#alertes` | `evenement`, `places` |
| `ventouse` | tous les exploitants | durée maximale dépassée | « Véhicule ventouse » / « A2 occupée depuis 2 h 01 (max 2 h). » | `/exploitant#alertes` | `evenement`, `places`, `duree_min` |
| `place_speciale` | tous les exploitants | place PMR ou électrique occupée sans autorisation | « Place spéciale » / « La place PMR A1 est occupée sans autorisation. » | `/exploitant#alertes` | `evenement`, `places`, `type_place` |
| `zone_interdite` | tous les exploitants | événement `zone_interdite` reçu du Pi | « Zone interdite » / « Un véhicule est arrêté dans allee1. » | `/exploitant#alertes` | `evenement`, `zone` |

Pour les quatre anomalies, `id` = l'identifiant de l'événement (`evt_…`).

### 3.3 Règles d'envoi (serveur)

- **Une notification par événement**, jamais une par image analysée.
- Réponses aux questions de 1.8 : le conducteur **ne reçoit pas** `ventouse` (il reçoit `place_a_liberer`) ; le préavis `fin_duree` est de **10 minutes** (paramètre `preavis_fin_duree_min`).
- Anomalies : `Urgency: high`, `TTL` 1 heure. Messages conducteur : `Urgency: normal`, `TTL` 15 minutes (un message périmé n'a plus d'intérêt).
- Si le service de push répond `404` ou `410`, l'abonnement est supprimé (1.5).
- Clés VAPID dans les variables d'environnement `VAPID_CLE_PUBLIQUE`, `VAPID_CLE_PRIVEE` et `VAPID_SUJET` (sujet `mailto:` de l'équipe, question 3 de 1.8 : adresse à choisir ensemble). Jamais dans Git.
- Pas de donnée personnelle dans le texte : ni nom, ni email, seulement des numéros de place.

---

## 4. Contrat plan de la maquette

Rédaction : Maissa, à valider par Amal (IoT) · Statut : proposition

### 4.1 Plan

Maquette de **60 × 40 cm**, 8 places en deux rangées de part et d'autre d'une allée centrale. On entre à gauche et on sort à droite. Repère : origine en haut à gauche, `x` vers la droite, `y` vers le bas, en centimètres (même sens que le SVG de la PWA). Schéma à l'échelle (1 caractère = 1 cm en largeur) :

```text
x (cm)  0             14      22      30      38      46  50        60
        +-------------+-------+-------+-------+-------+-------------+  y=0
        |             |  A1   |  A2   |  A3   |  A4   |             |
        |             | (PMR) |       |       |       |             |
        |             |   o   |   o   |   o   |   o   |             |
        +-------------+---|---+---|---+---|---+---|---+---+---------+  y=13
ENTRÉE  E=================I1======I2======I3======I4======|#########S  SORTIE
        |                 |       |       |       |       |# allee1#|
        +-------------+---|---+---|---+---|---+---|---+---+---------+  y=27
        |             |   o   |   o   |   o   |   o   |             |
        |             |  B1   |  B2   |  B3   |  B4   |             |
        |             |       |       |       |(ÉLEC) |             |
        +-------------+-------+-------+-------+-------+-------------+  y=40
```

Légende : `E` entrée, `S` sortie, `I1`…`I4` intersections de l'allée, `o` centre d'une place (nœud du graphe), `=` allée centrale, `#` zone interdite `allee1`.

- **Places** : 8 cm × 13 cm, pour des voitures miniatures au 1/43 (environ 10 × 4,5 cm) ou au 1/64 (environ 7,5 × 3,5 cm).
- **A1 = PMR** (la plus proche de l'entrée, côté rangée A), **B4 = électrique**, les autres sont normales.
- **Allée centrale** : de `y = 13` à `y = 27` (14 cm de large).
- **Zone interdite `allee1`** : la fin de l'allée devant la sortie, `x` de 50 à 60, `y` de 13 à 27. On peut y rouler, pas s'y arrêter.
- Les bandes `x` de 0 à 14 et de 46 à 60 au-dessus et au-dessous de l'allée sont des trottoirs (non stationnables).

| Place | Type | `duree_max_min` | Rectangle `[x, y, largeur, hauteur]` (cm) | Nœud |
|---|---|---|---|---|
| A1 | pmr | 120 | `[14, 0, 8, 13]` | `A1` |
| A2 | normale | 120 | `[22, 0, 8, 13]` | `A2` |
| A3 | normale | 120 | `[30, 0, 8, 13]` | `A3` |
| A4 | normale | 120 | `[38, 0, 8, 13]` | `A4` |
| B1 | normale | 120 | `[14, 27, 8, 13]` | `B1` |
| B2 | normale | 120 | `[22, 27, 8, 13]` | `B2` |
| B3 | normale | 120 | `[30, 27, 8, 13]` | `B3` |
| B4 | electrique | 60 | `[38, 27, 8, 13]` | `B4` |

Pour la démo, l'exploitant pourra baisser `duree_max_min` à 2 minutes afin de montrer une alerte `ventouse` sans attendre.

### 4.2 Graphe de guidage

Nœuds (coordonnées en cm sur le plan) :

| Nœud | Type | `x` | `y` | Rôle |
|---|---|---|---|---|
| `E` | entree | 0 | 20 | Entrée, départ du guidage |
| `I1` | intersection | 18 | 20 | Devant A1 et B1 |
| `I2` | intersection | 26 | 20 | Devant A2 et B2 |
| `I3` | intersection | 34 | 20 | Devant A3 et B3 |
| `I4` | intersection | 42 | 20 | Devant A4 et B4 |
| `S` | sortie | 60 | 20 | Sortie |
| `A1`…`A4` | place | 18, 26, 34, 42 | 6,5 | Centre de la place |
| `B1`…`B4` | place | 18, 26, 34, 42 | 33,5 | Centre de la place |

Arêtes (non orientées, longueur en cm) :

| Arête | Longueur | Arête | Longueur |
|---|---|---|---|
| `E` – `I1` | 18 | `I1` – `A1`, `I1` – `B1` | 13,5 |
| `I1` – `I2` | 8 | `I2` – `A2`, `I2` – `B2` | 13,5 |
| `I2` – `I3` | 8 | `I3` – `A3`, `I3` – `B3` | 13,5 |
| `I3` – `I4` | 8 | `I4` – `A4`, `I4` – `B4` | 13,5 |
| `I4` – `S` | 18 | | |

Distances depuis `E` : A1 et B1 = 31,5 cm ; A2 et B2 = 39,5 ; A3 et B3 = 47,5 ; A4 et B4 = 55,5. À égalité, la règle de 2.7 choisit la rangée A. Exemple : tout est libre et le conducteur n'a pas d'autorisation PMR → A1 est exclue, B1 est proposée (trajet `E → I1 → B1`).

### 4.3 Format JSON (`GET /api/parking`, `GET` et `PUT /api/config/plan`)

```json
{
  "parking": "P1",
  "nom": "Maquette SUP'COM",
  "dimensions_cm": [60, 40],
  "places": [
    { "id": "A1", "type": "pmr",        "rect_cm": [14, 0, 8, 13],  "noeud": "A1" },
    { "id": "A2", "type": "normale",    "rect_cm": [22, 0, 8, 13],  "noeud": "A2" },
    { "id": "A3", "type": "normale",    "rect_cm": [30, 0, 8, 13],  "noeud": "A3" },
    { "id": "A4", "type": "normale",    "rect_cm": [38, 0, 8, 13],  "noeud": "A4" },
    { "id": "B1", "type": "normale",    "rect_cm": [14, 27, 8, 13], "noeud": "B1" },
    { "id": "B2", "type": "normale",    "rect_cm": [22, 27, 8, 13], "noeud": "B2" },
    { "id": "B3", "type": "normale",    "rect_cm": [30, 27, 8, 13], "noeud": "B3" },
    { "id": "B4", "type": "electrique", "rect_cm": [38, 27, 8, 13], "noeud": "B4" }
  ],
  "zones_interdites": [ { "id": "allee1", "rect_cm": [50, 13, 10, 14] } ],
  "graphe": {
    "noeuds": [
      { "id": "E",  "type": "entree",       "x": 0,  "y": 20 },
      { "id": "I1", "type": "intersection", "x": 18, "y": 20 },
      { "id": "I2", "type": "intersection", "x": 26, "y": 20 },
      { "id": "I3", "type": "intersection", "x": 34, "y": 20 },
      { "id": "I4", "type": "intersection", "x": 42, "y": 20 },
      { "id": "S",  "type": "sortie",       "x": 60, "y": 20 },
      { "id": "A1", "type": "place", "x": 18, "y": 6.5 },  { "id": "B1", "type": "place", "x": 18, "y": 33.5 },
      { "id": "A2", "type": "place", "x": 26, "y": 6.5 },  { "id": "B2", "type": "place", "x": 26, "y": 33.5 },
      { "id": "A3", "type": "place", "x": 34, "y": 6.5 },  { "id": "B3", "type": "place", "x": 34, "y": 33.5 },
      { "id": "A4", "type": "place", "x": 42, "y": 6.5 },  { "id": "B4", "type": "place", "x": 42, "y": 33.5 }
    ],
    "aretes": [
      { "de": "E",  "a": "I1", "longueur_cm": 18 },
      { "de": "I1", "a": "I2", "longueur_cm": 8 },
      { "de": "I2", "a": "I3", "longueur_cm": 8 },
      { "de": "I3", "a": "I4", "longueur_cm": 8 },
      { "de": "I4", "a": "S",  "longueur_cm": 18 },
      { "de": "I1", "a": "A1", "longueur_cm": 13.5 }, { "de": "I1", "a": "B1", "longueur_cm": 13.5 },
      { "de": "I2", "a": "A2", "longueur_cm": 13.5 }, { "de": "I2", "a": "B2", "longueur_cm": 13.5 },
      { "de": "I3", "a": "A3", "longueur_cm": 13.5 }, { "de": "I3", "a": "B3", "longueur_cm": 13.5 },
      { "de": "I4", "a": "A4", "longueur_cm": 13.5 }, { "de": "I4", "a": "B4", "longueur_cm": 13.5 }
    ]
  }
}
```

Deux repères différents, reliés par l'identifiant des places et des zones :
- **centimètres** (`rect_cm`, graphe) : pour le plan SVG et le guidage ;
- **pixels de la caméra** (`poly`, section 5.2) : pour la détection sur le Pi.

### 4.4 Recommandations physiques

| Élément | Recommandation | Pourquoi |
|---|---|---|
| Fond | Carton ou mousse **mat, gris moyen** ; pas de surface brillante | Contraste avec les voitures, pas de reflets |
| Marquage | Ruban adhésif **blanc mat** de 1 cm, sur les bords des places ; zone interdite hachurée | Repères visibles pour dessiner les polygones |
| Voitures | 6 à 10 voitures de **couleurs vives** (rouge, bleu, jaune, blanc…) ; **pas de voiture noire** ni gris foncé | Le test S1 a montré des oublis sur les voitures sombres |
| Caméra | **Fixée** sur un support rigide, objectif **perpendiculaire** au plan, à 60–70 cm de haut environ (à ajuster pour voir toute la maquette) | Une caméra qui bouge oblige à redessiner tous les polygones |
| Cadrage | **Toutes les places entièrement visibles**, avec au moins 3 cm de marge autour de la maquette | Le test S1 a montré des oublis sur les voitures coupées au bord |
| Éclairage | **LED constante et diffuse** (deux sources de chaque côté), pas de lumière du jour directe ni d'ombre portée de la caméra | Même image le matin et le soir, pas de fausses détections |
| Résolution | 640 × 480 pour la détection (environ 10 pixels par cm) | Suffisant pour YOLO11n et rapide sur le Pi |

Après toute modification de la caméra ou de la maquette : nouvelle photo de référence, puis nouveau dessin des polygones (`PUT /api/config/polygones`).

---

## 5. MQTT et polygones

Rédaction : Amal (IoT) · Statut : **validé**, repris de [`iot/README.md`](../iot/README.md). Les ajouts proposés sont en 5.3, à part.

### 5.1 Contrat MQTT

| Topic | Exemple | Envoyé quand |
|---|---|---|
| `parking/P1/places/<id>` | `{"place":"A3","etat":"occupee","depuis":"2026-11-20T10:02:11Z"}` | changement d'état (retain, QoS 1) |
| `parking/P1/evenements` | `{"type":"cheval","places":["A3","A4"],"ts":"..."}` | début d'une anomalie |
| `parking/P1/evenements` | `{"type":"zone_interdite","zone":"allee1","ts":"..."}` | début d'une anomalie |
| `parking/P1/pi/statut` | `{"en_ligne":true,"fps":0.8,"temp_cpu":61}` | toutes les 60 s |

- `etat` vaut `libre` ou `occupee`.
- Le serveur calcule la règle « ventouse » à partir de `depuis`.
- Broker Mosquitto en **TLS sur le port 8883**.

### 5.2 Contrat des polygones (`places.json`)

| Champ | Contenu |
|---|---|
| `parking` | Identifiant du parking (`"P1"`) |
| `resolution` | `[largeur, hauteur]` de l'image de la caméra, en pixels |
| `places` | Liste ; chaque place a `id`, `type` (`normale`, `pmr`, `electrique`), `duree_max_min`, `poly` = liste de points `[x, y]` en pixels |
| `zones_interdites` | Liste ; chaque zone a `id` et `poly` |

Modèle : [`iot/places.exemple.json`](../iot/places.exemple.json) (4 places, exemple de format). Le vrai `places.json` de la maquette aura les 8 places et la zone `allee1` de la section 4, avec les types de 4.1.

### 5.3 Propositions d'ajout (à valider par Amal)

Ces ajouts ne changent rien aux messages de 5.1 ; ils complètent le contrat pour la configuration et la sécurité du broker.

| Topic | Sens | Exemple | Envoyé quand |
|---|---|---|---|
| `parking/P1/config` | serveur → Pi | le fichier `places.json` complet (5.2) | à chaque `PUT /api/config/polygones` ou `PATCH /api/config/places/{id}` (retain, QoS 1) |
| `parking/P1/pi/statut` | Pi → serveur | `{"en_ligne":false}` | **dernière volonté** (LWT, retain) : publiée par le broker si le Pi se déconnecte brutalement |

Comptes du broker (mot de passe obligatoire, pas de connexion anonyme) :

| Compte | Publie sur | S'abonne à |
|---|---|---|
| `pi` | `parking/P1/places/#`, `parking/P1/evenements`, `parking/P1/pi/statut` | `parking/P1/config` |
| `serveur` | `parking/P1/config` | `parking/P1/#` |
