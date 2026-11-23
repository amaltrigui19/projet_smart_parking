# Middleware Smart Parking (Jakarta EE 11, WildFly)

Serveur d'application : règles métier, guidage, API REST sécurisée par JWT, MongoDB, client MQTT.
Contrat des routes : [`docs/contrats.md`](../docs/contrats.md), section 2.

Projet Maven à trois modules (parent : `pom.xml`) :

| Module | Rôle | État |
|---|---|---|
| `iam/` | Identité et accès : comptes (collection `utilisateurs`), hachage PBKDF2, émission des JWT RS256, rôles `CONDUCTEUR` et `EXPLOITANT` | sprint 3 |
| `core/` | Métier : places, réservations, guidage, alertes, accès MongoDB (connexion unique `MongoConnection`), client MQTT. Ne dépend pas de l'API REST | sprint 3 : schéma MongoDB et DAO |
| `api/` | Routes REST (JAX-RS). Dépend de `iam` et de `core`, et produit `api.war` | `GET /api/sante`, `/api/auth/*`, `/api/config/polygones` |

Dépendances : `api` → `iam`, `core` ; `iam` → `core` (pour la connexion MongoDB partagée, `core/persistence/MongoConnection`). `core` ne dépend d'aucun autre module. Tous utilisent le même driver : `mongodb-driver-sync` 5.14.0.

## État (sprint 3) : authentification et calibration

| Route | Rôle | Réponses |
|---|---|---|
| `GET /api/sante` | public | `200`, ou `503` si MongoDB ou MQTT ne répond pas |
| `POST /api/auth/inscription` | public | `201` ; `400 requete_invalide` ; `409 email_existant`. Le compte créé est toujours `CONDUCTEUR` |
| `POST /api/auth/connexion` | public | `200` (`jeton`, `type`, `expire_dans`, `utilisateur`) ; `401 identifiants_invalides` ; `403 compte_desactive` |
| `GET /api/auth/moi` | connecté | `200` ; `401 non_authentifie` sans jeton valide |
| `GET /api/config/polygones` | EXPLOITANT | `200` avec `places.json` (contrat §5.2) ; `404 introuvable` s'il n'y a rien |
| `PUT /api/config/polygones` | EXPLOITANT | Corps : `places.json` en entier → `200` avec le fichier enregistré ; `400 config_invalide` ; `400 requete_invalide` (JSON mal formé) ; `404 introuvable` si le parking P1 n'existe pas (lancer le seed) |

Pour les deux routes `/api/config/polygones` : `401 non_authentifie` sans jeton valide, `403 acces_refuse` pour un conducteur.

Toutes les erreurs ont le format du contrat (§2.1) : `{"erreur": "code_court", "message": "texte lisible"}`, y compris quand WildFly refuse un jeton avant JAX-RS (page d'erreur 401 dans `web.xml`).

### Sécurité

- **Mots de passe** : PBKDF2-HMAC-SHA256, 600 000 itérations, sel aléatoire de 16 octets, comparaison en temps constant (`iam/HachageMotDePasse`). Le format stocké est `pbkdf2_sha256$itérations$sel$empreinte`. Le mot de passe en clair n'est ni stocké, ni journalisé, ni renvoyé.
- **Connexion** : même message `401` que l'email soit inconnu ou le mot de passe faux. Le calcul est le même dans les deux cas (`verifierFactice`), pour ne pas trahir l'existence d'un email par la durée de la réponse.
- **JWT** : RS256, valable 1 heure, avec les revendications du contrat §2.2 (`iss`, `sub` = `u_…`, `upn`, `role`, `groups`, `iat`, `exp`, plus `jti`). Il est signé par `iam/GenerateurJwt` (bibliothèque standard Java) et vérifié par **MicroProfile JWT** de WildFly (`@LoginConfig(authMethod = "MP-JWT")` sur `ApiApplication`). Les routes protégées portent `@RolesAllowed`, activé par `resteasy.role.based.security` dans `web.xml`.
- **Jeton refusé** s'il est absent, mal formé, signé avec une autre clé, modifié (par exemple un rôle changé) ou expiré.
- MicroProfile JWT rejette en `401` **tout** jeton invalide présenté, même sur une route publique. La PWA ne doit donc pas envoyer d'en-tête `Authorization` sur `inscription` et `connexion`, et elle doit effacer le jeton après un `401` (contrat §1.2).

## Clés RSA des JWT

```bash
./server/scripts/generer-cles-jwt.sh      # depuis la racine du dépôt ; refuse d'écraser des clés existantes
```

| Fichier (dans `server/jwt/`, ignoré par Git) | Utilisé par | Pour |
|---|---|---|
| `privee.pem` (PKCS#8, **secret**) | `iam/EmetteurJwt`, au premier jeton émis | **signer** les jetons à la connexion |
| `publique.pem` | MicroProfile JWT de WildFly (`api/src/main/resources/META-INF/microprofile-config.properties`) | **vérifier** chaque jeton reçu |

`server/docker-compose.yml` monte `server/jwt/` en lecture seule dans le conteneur `wildfly` (`/opt/smartparking/jwt`), et indique `JWT_CLE_PRIVEE_FICHIER=/opt/smartparking/jwt/privee.pem`. Autres possibilités : mettre le contenu PEM de la clé privée dans la variable `JWT_CLE_PRIVEE`, ou changer l'emplacement de la clé publique avec `MP_JWT_VERIFY_PUBLICKEY_LOCATION`.

- Changer les clés (`--forcer`) rend invalides tous les jetons déjà émis : les utilisateurs se reconnectent.
- Sur la VM, `privee.pem` doit être lisible par l'uid 1000 (utilisateur `jboss` du conteneur, qui est aussi l'utilisateur `ubuntu`).

## Compte exploitant de démo

L'inscription publique crée seulement des conducteurs (US-01). Pour la démo, si `EXPLOITANT_EMAIL` et `EXPLOITANT_MOT_DE_PASSE` sont définis dans `server/.env`, WildFly crée ce compte **EXPLOITANT** au démarrage s'il n'existe pas encore (`iam/CompteExploitantDemo`). Un compte existant n'est pas modifié, et le mot de passe n'apparaît jamais dans les journaux.

```bash
# dans server/.env (jamais commité)
EXPLOITANT_EMAIL=exploitant@smartparking.test
EXPLOITANT_MOT_DE_PASSE=<openssl rand -hex 12>
```

Journal attendu (`docker compose logs wildfly`) : `Compte exploitant de démo créé : exploitant@smartparking.test`, ou `… existe déjà, inchangé.`

## Lancer

Rien à installer à part Docker : l'image compile `api.war` avec Maven (Java 21), puis le déploie dans WildFly.

```bash
./server/scripts/generer-cles-jwt.sh       # une seule fois
cd server
docker compose up -d --build
curl -k https://localhost/api/sante
```

Procédure complète : [`docs/installation-serveur.md`](../docs/installation-serveur.md).

## Tester

**Tests unitaires** (80 tests : 29 dans `core` pour la conversion Java ↔ MongoDB, les identifiants et les modèles, 31 dans `iam` pour le hachage, le JWT et les entrées, 20 dans `api` pour la validation et la conversion des polygones), sans Java ni Maven sur le PC :

```bash
docker run --rm -v "$PWD/middleware:/src" -v smartparking-m2:/root/.m2 -w /src maven:3.9-eclipse-temurin-21 mvn -B test
```

(Git Bash sous Windows : préfixer par `MSYS_NO_PATHCONV=1`. Avec Java 21 et Maven installés : `cd middleware && mvn test`.)

Attendu : `Tests run: 6`, puis `Tests run: 31`, puis `Tests run: 20`, chaque fois avec `Failures: 0, Errors: 0`, puis `BUILD SUCCESS`.

**Test réel des routes**, serveur lancé :

```bash
./server/scripts/tester-auth.sh                                   # https://localhost
EXPLOITANT_EMAIL=... EXPLOITANT_MOT_DE_PASSE=... ./server/scripts/tester-auth.sh   # + compte de démo
```

Attendu : `… vérification(s) réussie(s), 0 en échec`. Le script vérifie :
- l'inscription (201, rôle forcé à `CONDUCTEUR`, email en double, email et mot de passe invalides, JSON mal formé) ;
- la connexion (forme de la réponse, contenu du JWT, mauvais mot de passe et email inconnu avec le même message) ;
- `/moi` (avec un jeton, sans jeton, jeton bidon, falsifié ou expiré).

À la main :

```bash
curl -k -X POST https://localhost/api/auth/inscription -H "Content-Type: application/json" \
  -d '{"nom":"Sara Ben Ali","email":"sara@exemple.tn","mot_de_passe":"motdepasse8"}'
curl -k -X POST https://localhost/api/auth/connexion -H "Content-Type: application/json" \
  -d '{"email":"sara@exemple.tn","mot_de_passe":"motdepasse8"}'
curl -k https://localhost/api/auth/moi -H "Authorization: Bearer <jeton>"
```

## Calibration : `GET` et `PUT /api/config/polygones`

C'est la route de la page de calibration (`frontend/calibration.html`). Le corps et la réponse ont le format de `places.json` (contrat §5.2) : `parking`, `resolution`, `places[id, type, duree_max_min, poly]`, `zones_interdites[id, poly]`.

**Règles de `400 config_invalide`** (`core/calibration/CalibrationValidator`). Ce sont les mêmes que dans la page de calibration, la fausse API et `frontend/outils/valider_places.py` :
- identifiant en double (places et zones confondues) ;
- polygone de moins de 3 points, point non entier, point hors de `resolution` (de 0 à largeur − 1 et de 0 à hauteur − 1), polygone plat ;
- `type` inconnu (`normale`, `pmr`, `electrique`) ;
- `duree_max_min` qui n'est pas un entier ≥ 1 ;
- place absente du plan (A1 à B4, contrat §4.3) ;
- `parking` différent de `P1`.

Toutes les erreurs sont dans `message`, séparées par ` ; ` : la page de calibration en affiche la liste.

**En base**, le `PUT` fait deux choses :
- les places passent par `SpotRepository.replacePolygons` : type, durée et polygone sont mis à jour, **l'état venant du Pi est conservé**, et les places absentes du fichier sont supprimées ;
- la résolution et les zones interdites passent par `ParkingRepository.save` : le nom, les dimensions et le `rect_cm` (plan en cm) des zones existantes sont gardés.

Le `GET` reconstruit le fichier depuis les deux collections, avec les places triées par identifiant. La publication du fichier au Pi (`parking/P1/config`, contrat §5.3) viendra au sprint suivant.

**Tester**, serveur lancé et données d'exemple chargées (`./server/scripts/executer-mongo.sh server/mongo/seed.js`) :

```bash
EXPLOITANT_EMAIL=... EXPLOITANT_MOT_DE_PASSE=... ./server/scripts/tester-polygones.sh
```

Attendu : `15 vérification(s) réussie(s), 0 en échec`. Le script vérifie :
- les rôles : 401 sans jeton, 403 pour un conducteur ;
- un `PUT` valide, puis la relecture au format du contrat ;
- en local, que l'état de A1 et le `rect_cm` de `allee1` sont conservés ;
- `400 config_invalide` avec 6 erreurs dans le même message, et le JSON mal formé ;
- qu'un `PUT` refusé ne modifie rien.

Le fichier de départ est remis à la fin.

**Relire pour la démo** (le jeton vient de `POST /api/auth/connexion` avec le compte exploitant) :

```bash
python frontend/outils/relire_polygones.py --url https://localhost --cacert server/certs/ca.pem --jeton <jwt-exploitant>
```

> Les polygones **provisoires** du seed (`server/mongo/seed.js`) touchent le bord de l'image : la rangée B a des points à `y = 480`, et `allee1` à `x = 640`. Une image en 640 × 480 va de 0 à 639 et de 0 à 479 : le serveur refuse donc de **réenregistrer** ces polygones tels quels, et `relire_polygones.py` les signale. Cela disparaît dès la vraie calibration ; la correction du seed est proposée à Amal (borner les points à 639 et 479).

## Structure

```
middleware/
├── pom.xml                    # parent : Java 21, Jakarta EE 11, modules iam, core, api
├── Dockerfile                 # Maven (compilation) puis WildFly ; contexte = ce dossier
├── iam/                       # dépend de core (MongoConnection) et du driver mongodb-driver-sync 5.14.0
│   ├── src/main/java/tn/supcom/smartparking/iam/
│   │   ├── Utilisateur.java, Role.java
│   │   ├── HachageMotDePasse.java       # PBKDF2
│   │   ├── ValidationInscription.java   # nom, email, mot de passe
│   │   ├── GenerateurJwt.java           # signature RS256 (JDK seul)
│   │   ├── EmetteurJwt.java             # lit la clé privée (JWT_CLE_PRIVEE[_FICHIER])
│   │   ├── DepotUtilisateurs.java       # collection utilisateurs, index unique email
│   │   ├── ServiceAuthentification.java # inscription, connexion
│   │   ├── CompteExploitantDemo.java    # compte de démo au démarrage
│   │   └── ErreurIam.java               # erreurs du contrat (statut, code, message)
│   └── src/test/java/…                  # 31 tests unitaires
├── core/
│   └── src/main/java/tn/supcom/smartparking/core/
│       ├── model/                       # Parking, Spot, ForbiddenZone, PlanRect, SpotType, SpotState
│       ├── persistence/                 # SpotRepository, ParkingRepository (interfaces) + implémentations Mongo
│       ├── calibration/                 # CalibrationManager (logique métier), Validator, Mapper
│       ├── exception/                   # BusinessException, NotFound, InvalidConfig, InvalidRequest
│       └── config/ParkingSettings.java  # id du parking et places du plan (MicroProfile Config)
└── api/
    └── src/main/
        ├── java/tn/supcom/smartparking/
        │   ├── ApiApplication.java      # JAX-RS sous /api, @LoginConfig MP-JWT
        │   ├── health/HealthResource.java   # /api/sante
        │   ├── auth/AuthResource.java   # /api/auth/inscription, connexion, moi
        │   ├── calibration/CalibrationResource.java  # /api/config/polygones, délègue à CalibrationManager
        │   ├── JsonBody.java            # lecture du corps JSON (400 requete_invalide)
        │   └── error/                   # format {erreur, message} pour toutes les erreurs (mappers)
        ├── resources/META-INF/microprofile-config.properties   # émetteur et clé publique
        └── webapp/WEB-INF/web.xml       # @RolesAllowed actif, page d'erreur 401 en JSON
```
## Base MongoDB : parkings et places (sprint 3)

Collections du contrat §2.14, dans `core/` :

| Classe | Rôle |
|---|---|
| `model/Parking`, `Spot`, `ForbiddenZone` | Modèles, avec les règles de validation (polygone ≥ 3 points, durée > 0) |
| `persistence/MongoConnection` | Connexion unique, configurée par MicroProfile Config (variables `MONGO_*`) |
| `persistence/MongoMapper` | Traduction Java ↔ documents MongoDB (noms en snake_case du contrat) |
| `persistence/SpotRepository`, `ParkingRepository` | Interfaces d'accès aux données, implémentées par `MongoSpotRepository` et `MongoParkingRepository`. `replacePolygons` enregistre une calibration sans effacer l'état des places |
| `calibration/CalibrationManager` | Logique de la calibration : la ressource REST ne fait que déléguer |
| `config/ParkingSettings` | `smartparking.parking.id` (P1 par défaut) et `smartparking.parking.spot-ids`, surchargeables par variable d'environnement |

### Données d'exemple (seed)

P1 et ses 8 places (contrat 4.3). Les polygones sont **provisoires** : ils sont calculés à partir du plan en cm, en attendant la calibration réelle.

Depuis la racine du dépôt, MongoDB démarré (`docker compose -f server/docker-compose.yml up -d mongo`) :

```bash
# Linux / Git Bash
./server/scripts/executer-mongo.sh server/mongo/seed.js     # remplit la base (relançable, sans doublons)
./server/scripts/executer-mongo.sh server/mongo/relire.js   # affiche les places et leurs polygones
```

```powershell
# Windows PowerShell
.\server\scripts\executer-mongo.ps1 server\mongo\seed.js
.\server\scripts\executer-mongo.ps1 server\mongo\relire.js
```

Résultat attendu : `Seed terminé : 1 parking, 8 places.`, puis 8 lignes de places et la zone `allee1`.

### Tests

```bash
cd middleware && mvn test     # 29 tests dans core (conversion, identifiants, modèles), sans base
```

## Base MongoDB : schéma 2 (sprint 4)

Collections `autorisations`, `reservations` et `evenements` du contrat §2.14, dans `core/`. La collection des comptes appartient au module IAM (`iam/repository/MongoUserRepository`). Les routes qui les utilisent viennent aux sprints 6 à 8 : ce sprint prépare le modèle, l'accès aux données et les index.

| Classe | Rôle |
|---|---|
| `model/Permission`, `PermissionType` | Autorisation `pmr` / `electrique`, `isValidAt(instant)` (début inclus, fin exclue, `end = null` = sans fin) |
| `model/Reservation`, `ReservationState`, `Payment` | États du contrat §2.8 ; `ReservationState.OPEN` = `active`, `en_cours` |
| `model/Event`, `EventType`, `EventSource` | Anomalies `cheval`, `zone_interdite` (Pi), `ventouse`, `place_speciale` (serveur) |
| `persistence/ApiIds` | `ObjectId` en base ↔ `u_…`, `r_…`, `evt_…`, `aut_…` dans l'API |
| `persistence/ActivityMapper` | Traduction Java ↔ documents MongoDB. Les références à un utilisateur (`utilisateur`, `accordee_par`, `traite_par`) sont des `ObjectId`, comme `utilisateurs._id` |
| `persistence/PermissionRepository` (+ `MongoPermissionRepository`) | `create`, `findByUser`, `hasValid(userId, type, instant)`, `delete` |
| `persistence/ReservationRepository` (+ `MongoReservationRepository`) | `create`, `findById`, `list(userId, state, limit)`, `findOpenByUser`, `findOpenBySpot`, `findOpen`, `update(r, expectedState)` |
| `persistence/EventRepository` (+ `MongoEventRepository`) | `create`, `findById`, `list(handled, type, from, to, limit)`, `countUnhandled`, `markHandled`, `close` |

### Index

| Collection | Index | Pour |
|---|---|---|
| `autorisations` | `utilisateur_type` : `{utilisateur, type}` | « ce conducteur a-t-il une autorisation PMR ? » |
| `reservations` | `utilisateur_creee_le` : `{utilisateur, creee_le: -1}` | `GET /api/reservations` (historique, du plus récent au plus ancien) |
| `reservations` | `place_etat` : `{place, etat}` | réservations d'une place (plan : place réservée) |
| `reservations` | `utilisateur_ouverte_unique` : `{utilisateur}` unique, **partiel** sur `etat ∈ {active, en_cours}` | une seule réservation ouverte par conducteur → `409 reservation_deja_active` |
| `reservations` | `place_ouverte_unique` : `{place}` unique, **partiel** sur `etat ∈ {active, en_cours}` | une seule réservation ouverte par place → `409 place_indisponible` |
| `evenements` | `traite_debut` : `{traite, debut: -1}` | alertes non traitées |
| `evenements` | `type_debut` : `{type, debut: -1}` | filtre par type et par date |

Les deux index uniques partiels ne sont pas dans le contrat : ils font respecter les règles du §2.8 **par la base**, même si deux requêtes arrivent en même temps. `MongoReservationRepository` les traduit en `ReservationConflictException` (motif `USER` ou `SPOT`), que l'API traduira en `409`. Une réservation terminée, payée, annulée ou expirée n'est pas concernée : l'historique peut en contenir autant qu'on veut.

Les index sont créés par les DAO au premier accès **et** par `server/mongo/index.js`, avec les mêmes noms et options. Si on en change un, il faut changer l'autre : sinon MongoDB refuse avec `IndexOptionsConflict`.

### Données d'exemple et vérification

Après `seed.js` (P1 et ses places), depuis la racine du dépôt :

```bash
./server/scripts/executer-mongo.sh server/mongo/index.js            # crée et liste les index
./server/scripts/executer-mongo.sh server/mongo/exemples.js         # 2 conducteurs, 1 autorisation, 4 réservations, 4 événements
./server/scripts/executer-mongo.sh server/mongo/verifier-schema.js  # index utilisés par les requêtes, unicité des réservations ouvertes
```

- `exemples.js` est relançable (identifiants fixes, upsert ; les dates sont recalculées par rapport à maintenant). Il crée deux conducteurs `sara@exemple.tn` et `youssef@exemple.tn` (mot de passe de test dans le script), une autorisation PMR pour Sara, des réservations dans plusieurs états (dont une **active sur B3**, utile pour le plan SVG) et un événement de chaque type. **Local seulement** : le mot de passe est connu, ne pas le lancer sur la VM.
- `verifier-schema.js` vérifie, avec `explain()`, que chaque requête de l'API utilise son index, et que la base refuse une 2ᵉ réservation ouverte. Il ne laisse aucune trace. Résultat attendu : `0 en échec`.