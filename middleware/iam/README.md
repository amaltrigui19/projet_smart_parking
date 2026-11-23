# IAM : identité et accès

Module autonome qui gère les **comptes**, le **hachage des mots de passe** (Argon2id), l'**émission des JWT** (RS256, avec `kid`) et la **publication des clés publiques** (JWKS). Il ne dépend d'aucun autre module du projet : ni `core`, ni `api`.

Pour l'instant, il est livré comme un jar embarqué dans `api.war`, donc ses routes sont sous `/api`. Le passage à un `iam.war` séparé est décrit à la fin.

## Routes

| Route | Accès | Réponse |
|---|---|---|
| `POST /api/auth/register` | public | `201 {id, name, email, role, active}` · 400 `invalid_request` · 409 `email_already_exists` |
| `POST /api/auth/login` | public | `200 {access_token, type: "Bearer", expires_in, user}` · 401 `invalid_credentials` · 403 `account_disabled` |
| `GET /api/auth/me` | `Authorization: Bearer <jwt>` | `200 {id, name, email, role, active, permissions}` · 401 `unauthenticated` · 403 `account_disabled` |
| `GET /api/.well-known/jwks.json` | public | `200 {"keys": [...]}` : les clés publiques (JWK), mises en cache 5 minutes |

- **Erreurs** : `{"error": "code", "message": "texte en français"}`.
- **Inscription** : elle crée toujours un compte `DRIVER`. Un champ `role` envoyé par le client est ignoré.
- **Connexion** : la réponse est la même que l'email ou le mot de passe soit faux. Pour un email inconnu, le calcul Argon2 est fait quand même, avec la même durée.

## Structure

Paquet `tn.supcom.smartparking.iam` :

| Dossier | Rôle |
|---|---|
| `api/auth/` | Routes JAX-RS `register`, `login`, `me` (`AuthEndpoint`) et objets échangés (`RegisterRequest`, `LoginRequest`, `TokenResponse`, `UserResponse`). Aucune logique. |
| `api/jwk/` | Route `/.well-known/jwks.json` (`JwksEndpoint`). |
| `api/error/` | `IamExceptionMapper` : `IamException` → `{"error", "message"}`. |
| `controllers/` | Passage entre HTTP et les managers : lecture du JSON et de l'en-tête `Authorization`, mise en forme des réponses, effacement des mots de passe. |
| `managers/` | **Toute la logique** : `AuthenticationManager` (inscription, connexion, re-hachage, utilisateur courant), `JwksManager`, `OperatorAccountInitializer` (compte de démo). |
| `core/security/` | `PasswordHasher` (interface) et `Argon2Utility` (Argon2id), `Secrets` (effacement mémoire). |
| `core/token/` | `TokenIssuer` / `JwtTokenIssuer` (création), `TokenValidator` / `JwtTokenValidator` (vérification), avec Nimbus JOSE+JWT. |
| `core/validation/` | `RegistrationValidator` (interface) et ses règles : nom, email, mot de passe de 8 à 128 caractères. |
| `keyresolver/` | `KeyResolver` (`resolve(kid)`, `signingKey()`, `publicKeys()`) et `FileKeyResolver` (plusieurs clés, rotation). |
| `repository/` | `UserRepository` (interface), `MongoUserRepository` (collection `users`) et `IamMongoConnection` (connexion propre à l'IAM). |
| `model/`, `exceptions/` | `User`, `Role` (`DRIVER`, `OPERATOR`) et `IamException`. |

Chaque point qu'on peut vouloir changer est une **interface** : hachage, jetons, validation, dépôt, clés. On peut ainsi remplacer une implémentation sans toucher aux managers, et les simuler dans les tests (Mockito).

## Choix techniques

- **Argon2id** avec **Bouncy Castle** (`bcprov-jdk18on`).
  - C'est du Java pur : pas de bibliothèque native à installer dans l'image Docker.
  - C'est la bibliothèque de cryptographie de référence en Java.
  - Seule son API « légère » est utilisée (aucun fournisseur JCA à enregistrer), ce qui nous laisse la main sur la comparaison en temps constant (`MessageDigest.isEqual`) et sur l'effacement des `char[]` et `byte[]`.
  - Format PHC standard : `$argon2id$v=19$m=19456,t=2,p=1$<sel>$<empreinte>`, compatible avec les autres implémentations. Les tests vérifient des empreintes produites par argon2-cffi.
- **Paramètres Argon2** : recommandation OWASP (19 Mio, 2 passes, 1 voie), réglable par `ARGON2_*`. S'ils changent, l'empreinte d'un compte est refaite à sa prochaine connexion (`needsRehash`).
- **JWT** avec **Nimbus JOSE+JWT**, la bibliothèque JWT de référence en Java. Elle sait produire un JWKS sans écrire de JSON de clé à la main.
- **Vérification d'un jeton** :
  - RS256 uniquement (`none` et HS256 refusés) ;
  - `kid` connu, signature, `iss`, `exp` et `iat` avec 30 s de tolérance ;
  - `sub` en `u_…` et `role` valide.
- **Revendications** : `iss`, `sub`, `upn`, `role`, `groups`, `iat`, `exp`, `jti` ; durée de vie de 1 heure.

## Configuration (MicroProfile Config)

Ce sont des variables d'environnement de WildFly (`server/docker-compose.yml`) :

| Variable | Défaut | Rôle |
|---|---|---|
| `MONGO_HOST`, `MONGO_PORT` | `mongo`, `27017` | Serveur MongoDB |
| `MONGO_USER`, `MONGO_PASSWORD` | (aucun) | Compte MongoDB (authentifié sur `MONGO_AUTH_DATABASE`, `admin` par défaut) |
| `MONGO_DATABASE` | `smartparking` | Base (collection `users`) |
| `JWT_KEYS_DIR` | `/opt/smartparking/jwt/keys` | Dossier des clés (rotation), voir plus bas |
| `JWT_ACTIVE_KID` | la dernière clé privée par ordre alphabétique | Clé qui signe |
| `JWT_PRIVATE_KEY` / `JWT_PRIVATE_KEY_FILE` | (aucun) | Une seule clé (PEM, ou chemin du fichier), prioritaire sur le dossier. Son `kid` vaut `JWT_KEY_ID`, sinon l'empreinte RFC 7638. |
| `JWT_ISSUER`, `JWT_LIFETIME_SECONDS` | `smartparking`, `3600` | Émetteur et durée de vie des jetons |
| `ARGON2_MEMORY_KIB`, `ARGON2_ITERATIONS`, `ARGON2_PARALLELISM` | `19456`, `2`, `1` | Paramètres Argon2id |
| `OPERATOR_EMAIL`, `OPERATOR_PASSWORD`, `OPERATOR_NAME` | (aucun), (aucun), `Opérateur démo` | Compte de démo `OPERATOR`, créé au démarrage s'il n'existe pas |

Les clés et les mots de passe ne vont **jamais** dans Git (`*.pem` et `server/jwt/` sont ignorés), ni dans les journaux : seul le `kid` est journalisé.

## Clés, JWKS et rotation

### Le dossier des clés

Chaque fichier `<kid>.pem` du dossier `JWT_KEYS_DIR` est une clé, nommée par exemple d'après le mois de sa création :

```
server/jwt/keys/
├── 2026-10.pem   # clé PUBLIQUE seule : ancienne clé, encore publiée, ne signe plus
├── 2026-11.pem   # clé PRIVÉE : publiée, peut signer
└── 2026-12.pem   # clé PRIVÉE : la dernière par ordre alphabétique, c'est elle qui signe
```

- Une **clé privée** (PKCS#8, `-----BEGIN PRIVATE KEY-----`) peut signer et est publiée.
- Une **clé publique** seule (`-----BEGIN PUBLIC KEY-----`) est publiée sans signer : c'est une ancienne clé, gardée le temps que ses jetons expirent.
- Le dossier est **relu toutes les 60 s** : une rotation ne demande pas de redémarrage.

Créer une clé :

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out server/jwt/keys/2026-12.pem
```

### Le JWKS

`GET /.well-known/jwks.json` publie toutes les clés publiques, au format JWK (`kty`, `n`, `e`, `kid`, `use: sig`, `alg: RS256`), sans aucune partie privée. Chaque jeton porte le `kid` de sa clé dans son en-tête : celui qui vérifie (l'API) prend la clé du JWKS qui a ce `kid`.

Côté API (MicroProfile JWT), il suffit de pointer `mp.jwt.verify.publickey.location` vers le JWKS. Plus besoin de `publique.pem`.

### Rotation, pas à pas

1. **Déposer** la nouvelle clé privée (`2026-12.pem`). Moins de 60 s plus tard, elle signe les nouveaux jetons et apparaît dans le JWKS. Les jetons déjà émis, avec `kid=2026-11`, restent valables, car `2026-11` est toujours publiée.
   - Pour publier la nouvelle clé **avant** qu'elle ne signe : fixer `JWT_ACTIVE_KID=2026-11`, déposer `2026-12.pem`, attendre, puis passer `JWT_ACTIVE_KID=2026-12`.
2. **Après 1 heure** (la durée de vie des jetons), remplacer `2026-11.pem` par sa clé publique seule, ou le supprimer :
   ```bash
   openssl pkey -in server/jwt/keys/2026-11.pem -pubout -out /tmp/2026-11.pub && mv /tmp/2026-11.pub server/jwt/keys/2026-11.pem
   ```
3. Plus tard, supprimer `2026-11.pem` : la clé disparaît du JWKS.

Testé dans Docker : une clé déposée pendant que WildFly tourne est prise en compte en environ 60 s. Les jetons de l'ancienne et de la nouvelle clé sont acceptés, aussi par MicroProfile JWT via le JWKS.

## Comptes de démo et anciens comptes

- La collection `users` (champs en anglais, empreintes Argon2id) **remplace** `utilisateurs`. Les anciens comptes (PBKDF2) ne sont pas repris : il n'y a plus aucun code PBKDF2.
- Au démarrage, le compte `OPERATOR` de `OPERATOR_EMAIL` / `OPERATOR_PASSWORD` est créé s'il n'existe pas. Les comptes de test se réinscrivent.
- L'ancienne collection `utilisateurs` peut être supprimée à la main : `db.utilisateurs.drop()`.

## Tests

```bash
# Depuis middleware/, sans Maven ni JDK 21 sur le PC :
docker run --rm -v "$PWD:/src" -v sp-m2:/root/.m2 -w /src maven:3.9-eclipse-temurin-21 mvn -B -pl iam test
```

Il y a 63 tests unitaires :
- clés et rotation ;
- jetons : falsifié, autre clé, `kid` inconnu, expiré, `alg=none` et HS256 refusés ;
- JWKS ;
- Argon2id, y compris la compatibilité avec argon2-cffi ;
- managers (Mockito), controller, validation, conversion MongoDB, compte de démo.

Toujours lancer `mvn clean package` avant de déployer un `api.war` construit en local : sinon, d'anciennes classes compilées peuvent rester dans `target/`.

## Passer à `iam.war` (avant la démo finale)

Le module est déjà écrit pour être séparé : aucune dépendance vers `core` ou `api`, sa propre configuration, sa propre connexion MongoDB, son propre format d'erreur. Les étapes :

1. **Maven** : dans `iam/pom.xml`, passer à `<packaging>war</packaging>` avec `<finalName>iam</finalName>`. Ajouter une classe `IamApplication` (`@ApplicationPath("/")`).
   - Ajouter un `WEB-INF/jboss-web.xml` avec `<context-root>/iam</context-root>` (ou `/auth`).
   - Retirer la dépendance `iam` de `api/pom.xml`.
2. **Docker** : copier aussi `iam/target/iam.war` dans `standalone/deployments/` (`middleware/Dockerfile`). Le même WildFly sert alors les deux applications. On peut aussi en faire un conteneur à part.
3. **Nginx** (`server/nginx/nginx.conf`) :
   - `location /api/auth/` → `proxy_pass http://wildfly:8080/iam/auth/` ;
   - `location = /.well-known/jwks.json` → `proxy_pass http://wildfly:8080/iam/.well-known/jwks.json` ;
   - le reste de `/api/` ne change pas.
4. **API** : `mp.jwt.verify.publickey.location=http://wildfly:8080/iam/.well-known/jwks.json`, l'adresse interne (ou `http://localhost:8080/iam/...` si c'est le même WildFly). L'API ne fait alors plus que **vérifier** les jetons, comme avec un fournisseur d'identité externe.
5. **`/me`** : rien à changer, l'IAM vérifie déjà ses jetons lui-même (`TokenValidator`). Il faut seulement que `iam.war` n'active pas MicroProfile JWT (pas de `@LoginConfig`), pour ne pas dépendre de la config de l'API.
6. **Variables** : `iam.war` lit les mêmes variables (`MONGO_*`, `JWT_*`, `OPERATOR_*`). Seul le conteneur de l'IAM a besoin de la clé **privée** : l'API n'a plus besoin d'aucune clé.
7. **Tests** : refaire le test réel (JWKS, register, login, me, jeton falsifié refusé), puis la PWA de bout en bout.
