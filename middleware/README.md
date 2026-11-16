# Middleware Smart Parking (Jakarta EE 11, WildFly)

Serveur d'application : règles métier, guidage, API REST sécurisée par JWT, MongoDB, client MQTT.
Contrat des routes : [`docs/contrats.md`](../docs/contrats.md), section 2.

Projet Maven à trois modules (parent : `pom.xml`) :

| Module | Rôle | État |
|---|---|---|
| `iam/` | Identité et accès : comptes, hachage des mots de passe, émission et vérification des JWT, rôles `CONDUCTEUR` et `EXPLOITANT`, filtres de sécurité | vide, prévu au sprint 3 |
| `core/` | Métier : places, réservations, guidage, alertes, accès MongoDB, client MQTT. Ne dépend pas de l'API REST | vide, prévu à partir du sprint 4 |
| `api/` | Routes REST (JAX-RS). Dépend de `iam` et de `core`, et produit `api.war` | `GET /api/sante` |

Dépendances : `api` → `iam`, `core`. `iam` et `core` sont indépendants l'un de l'autre.

## État (sprint 1)

Application de test : une seule route, `GET /api/sante`, qui répond `{"statut":"ok", ...}` et vérifie que MongoDB et le broker MQTT sont joignables. Elle sert à tester le déploiement (Nginx en HTTPS → WildFly).

## Lancer

Rien à installer à part Docker : l'image compile `api.war` avec Maven (Java 21) puis le déploie dans WildFly.

```bash
cd server
docker compose up -d --build
curl -k https://localhost/api/sante
```

Procédure complète : [`docs/installation-serveur.md`](../docs/installation-serveur.md).

Compiler sans Docker demande Java 21 et Maven : `cd middleware && mvn package -DskipTests` produit `api/target/api.war`.

## Structure

```
middleware/
├── pom.xml                    # parent : Java 21, Jakarta EE 11, modules iam, core, api
├── Dockerfile                 # Maven (compilation) puis WildFly ; contexte = ce dossier
├── iam/
│   └── src/main/java/tn/supcom/smartparking/iam/
├── core/
│   └── src/main/java/tn/supcom/smartparking/core/
└── api/
    └── src/main/java/tn/supcom/smartparking/
        ├── ApiApplication.java    # JAX-RS, toutes les routes sous /api
        └── sante/SanteResource.java
```
## Base MongoDB : parkings et places (sprint 3)

Collections du contrat §2.14, dans `core/` :

| Classe | Rôle |
|---|---|
| `modele/Parking`, `Place`, `ZoneInterdite` | Modèles, avec les règles de validation (polygone ≥ 3 points, durée > 0) |
| `donnees/ConnexionMongo` | Connexion unique, configurée par les variables `MONGO_*` |
| `donnees/ConversionMongo` | Traduction Java ↔ documents MongoDB (noms en snake_case du contrat) |
| `donnees/DepotParkings`, `DepotPlaces` | DAO. `DepotPlaces.remplacerPolygones` enregistre une calibration sans effacer l'état des places |

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
cd middleware && mvn test     # 6 tests de conversion Java ↔ MongoDB, sans base
```