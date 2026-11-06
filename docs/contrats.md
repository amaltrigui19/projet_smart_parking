# Contrats d'interface

Les trois parties du projet (IoT, serveur, PWA) sont développées en parallèle par des personnes différentes. Un **contrat** fixe ce qu'une partie envoie ou attend d'une autre : tant qu'il est respecté, chacune avance sans attendre les autres, avec des données simulées si besoin.

**Un seul fichier, une section par partie.** Chaque section est écrite par la personne du domaine, puis relue par les deux autres. Un contrat ne change jamais sans l'accord des trois : on ouvre une PR qui ne touche que sa section et on prévient dans le groupe.

| Section | Rédaction | Statut |
|---|---|---|
| [1. Contrat PWA](#1-contrat-pwa) | Eya | Brouillon, à relire |
| [2. Contrat API REST](#2-contrat-api-rest) | Maissa (serveur) | À rédiger |
| [3. Contrat plan de la maquette](#3-contrat-plan-de-la-maquette) | Amal (IoT) | À rédiger |
| [4. MQTT et polygones](#4-mqtt-et-polygones) | Amal (IoT) | Rédigés dans `iot/README.md`, à valider |

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

La PWA fournit l'outil qui dessine les places sur une photo de la maquette. Il produit un fichier au format de `iot/places.exemple.json` (`parking`, `resolution`, `places[id, type, duree_max_min, poly]`, `zones_interdites`), coordonnées **en pixels de l'image de la caméra**. Le serveur l'enregistre et le Pi le lit : le format est donc celui de la section 4.

### 1.7 Ce que la PWA attend de la maquette (section 3)

Pour dessiner le plan SVG et le trajet : la **liste des places** avec leur numéro, la **résolution de l'image** de la caméra et le **graphe** du parking (nœuds, coordonnées sur le plan, arêtes). Sans graphe, le trajet n'est pas affichable.

### 1.8 Questions ouvertes

1. Le conducteur reçoit-il `ventouse` quand c'est son véhicule ? (proposition : non, seulement `place_a_liberer`)
2. Délai de `fin_duree` : 10 minutes avant la fin ? (proposition : oui)
3. Adresse d'équipe pour le sujet VAPID (`mailto:`) : laquelle ?
4. L'état `reservee` d'une place : porté par l'API des places, ou à déduire des réservations ?

---

## 2. Contrat API REST

Rédaction : Maissa (serveur) · Statut : à rédiger

_À compléter : routes, rôles, corps de requête et de réponse, codes d'erreur. Voir les besoins de la PWA en 1.3 à 1.5._

---

## 3. Contrat plan de la maquette

Rédaction : Amal (IoT) · Statut : à rédiger

_À compléter : dimensions de la maquette, numérotation des places, zones interdites, graphe (nœuds, coordonnées, arêtes pondérées). Voir 1.7._

---

## 4. MQTT et polygones

Rédaction : Amal (IoT) · Statut : rédigés dans [`iot/README.md`](iot/README.md), à valider ensemble le vendredi du sprint 1.
