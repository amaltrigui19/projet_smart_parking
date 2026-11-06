# API Smart Parking (Jakarta EE 11, WildFly)

Serveur : règles métier, guidage, API REST sécurisée par JWT, MongoDB, client MQTT.
Contrat des routes : [`docs/contrats.md`](../docs/contrats.md), section 2.

## État (sprint 1)

Application de test : une seule route, `GET /api/sante`, qui répond `{"statut":"ok", ...}` et vérifie que MongoDB et le broker MQTT sont joignables. Elle sert à tester le déploiement (Nginx en HTTPS → WildFly).

## Lancer

Rien à installer à part Docker : l'image compile le WAR avec Maven puis le déploie dans WildFly.

```bash
cd infra
docker compose up -d --build
curl -k https://localhost/api/sante
```

Procédure complète : [`docs/installation-serveur.md`](../docs/installation-serveur.md).

## Structure

```
api/
├── Dockerfile                 # Maven (compilation) puis WildFly
├── pom.xml                    # Jakarta EE 11, Java 21, api.war
└── src/main/java/tn/supcom/smartparking/
    ├── ApiApplication.java    # JAX-RS, toutes les routes sous /api
    └── sante/SanteResource.java
```
