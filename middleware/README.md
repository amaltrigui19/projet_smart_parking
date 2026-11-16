# Middleware Smart Parking (Jakarta EE 11, WildFly)

Serveur d'application : règles métier, guidage, API REST sécurisée par JWT, MongoDB, client MQTT.
Contrat des routes : [`docs/contrats.md`](../docs/contrats.md), section 2.

Projet Maven à trois modules (parent : `pom.xml`) :

| Module | Rôle | État |
|---|---|---|
| `iam/` | Identité et accès : comptes (collection `utilisateurs`), hachage PBKDF2, émission des JWT RS256, rôles `CONDUCTEUR` et `EXPLOITANT` | sprint 3 |
| `core/` | Métier : places, réservations, guidage, alertes, accès MongoDB, client MQTT. Ne dépend pas de l'API REST | en cours (sprint 3, schéma MongoDB et DAO) |
| `api/` | Routes REST (JAX-RS). Dépend de `iam` et de `core`, et produit `api.war` | `GET /api/sante`, `/api/auth/*` |

Dépendances : `api` → `iam`, `core`. `iam` et `core` sont indépendants l'un de l'autre.

## État (sprint 3) : authentification

| Route | Rôle | Réponses |
|---|---|---|
| `GET /api/sante` | public | `200`, ou `503` si MongoDB ou MQTT ne répond pas |
| `POST /api/auth/inscription` | public | `201` ; `400 requete_invalide` ; `409 email_existant`. Le compte créé est toujours `CONDUCTEUR` |
| `POST /api/auth/connexion` | public | `200` (`jeton`, `type`, `expire_dans`, `utilisateur`) ; `401 identifiants_invalides` ; `403 compte_desactive` |
| `GET /api/auth/moi` | connecté | `200` ; `401 non_authentifie` sans jeton valide |

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

**Tests unitaires** (31 tests : hachage, JWT, validation des entrées), sans Java ni Maven sur le PC :

```bash
docker run --rm -v "$PWD/middleware:/src" -v smartparking-m2:/root/.m2 -w /src maven:3.9-eclipse-temurin-21 mvn -B test
```

(Git Bash sous Windows : préfixer par `MSYS_NO_PATHCONV=1`. Avec Java 21 et Maven installés : `cd middleware && mvn test`.)

Attendu : `Tests run: 31, Failures: 0, Errors: 0` puis `BUILD SUCCESS`.

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

## Structure

```
middleware/
├── pom.xml                    # parent : Java 21, Jakarta EE 11, modules iam, core, api
├── Dockerfile                 # Maven (compilation) puis WildFly ; contexte = ce dossier
├── iam/                       # dépend du driver MongoDB (mongodb-driver-sync)
│   ├── src/main/java/tn/supcom/smartparking/iam/
│   │   ├── Utilisateur.java, Role.java
│   │   ├── HachageMotDePasse.java       # PBKDF2
│   │   ├── ValidationInscription.java   # nom, email, mot de passe
│   │   ├── GenerateurJwt.java           # signature RS256 (JDK seul)
│   │   ├── EmetteurJwt.java             # lit la clé privée (JWT_CLE_PRIVEE[_FICHIER])
│   │   ├── ConnexionMongo.java          # MONGO_* ; à regrouper avec core plus tard
│   │   ├── DepotUtilisateurs.java       # collection utilisateurs, index unique email
│   │   ├── ServiceAuthentification.java # inscription, connexion
│   │   ├── CompteExploitantDemo.java    # compte de démo au démarrage
│   │   └── ErreurIam.java               # erreurs du contrat (statut, code, message)
│   └── src/test/java/…                  # 31 tests unitaires
├── core/
│   └── src/main/java/tn/supcom/smartparking/core/
└── api/
    └── src/main/
        ├── java/tn/supcom/smartparking/
        │   ├── ApiApplication.java      # JAX-RS sous /api, @LoginConfig MP-JWT
        │   ├── sante/SanteResource.java
        │   ├── auth/AuthResource.java   # /api/auth/inscription, connexion, moi
        │   └── erreurs/                 # format {erreur, message} pour toutes les erreurs
        ├── resources/META-INF/microprofile-config.properties   # émetteur et clé publique
        └── webapp/WEB-INF/web.xml       # @RolesAllowed actif, page d'erreur 401 en JSON
```
