# Diagramme de déploiement

Source : [`server/docker-compose.yml`](../../server/docker-compose.yml), [`docs/installation-serveur.md`](../installation-serveur.md) et cahier des charges. L'état décrit est celui **vérifié le 10 novembre 2026** sur la VM.

```mermaid
flowchart LR
    subgraph maquette ["Maquette du parking (sur site)"]
        direction TB
        cam["Caméra<br/>module Pi ou webcam USB"]
        pi["Raspberry Pi 4<br/>Python, OpenCV, YOLO11n<br/>client MQTT"]
        cam -->|"images (jamais envoyées)"| pi
    end

    subgraph vm ["VM AWS EC2 t3.small, Ubuntu 24.04, Docker Compose<br/>smartparking-supcom.duckdns.org, IP 13.49.171.243"]
        direction TB
        subgraph docker ["Conteneurs Docker"]
            direction TB
            nginx["nginx<br/>ports 80 et 443<br/>sert le frontend, proxy /api"]
            wildfly["wildfly (api.war)<br/>port 8080, interne<br/>modules iam, core, api"]
            mqtt["mqtt : Mosquitto<br/>port 8883, TLS<br/>comptes pi et serveur"]
            mongo["mongo<br/>port 27017<br/>sur 127.0.0.1 seulement"]
        end
        certbot["Certbot sur l'hôte<br/>renouvellement webroot<br/>copie vers server/certs"]
    end

    nav["Navigateur ou mobile<br/>PWA : conducteur et exploitant"]
    le["Let's Encrypt<br/>certificat HTTPS et TLS"]
    dns["DuckDNS<br/>nom de domaine"]
    push["Service de push<br/>Google, Mozilla, Apple"]

    pi -->|"MQTT sur TLS, 8883"| mqtt
    nav -->|"HTTPS 443, JWT"| nginx
    nav -.->|"HTTP 80, redirigé vers HTTPS"| nginx
    nginx -->|"proxy /api et /api/ws"| wildfly
    wildfly -->|"MongoDB, authentifié"| mongo
    wildfly -->|"abonné à parking/P1/#, publie parking/P1/config"| mqtt
    wildfly -.->|"Web Push, clés VAPID"| push
    push -.->|"notification"| nav
    certbot -.->|"défi HTTP-01 sur le port 80"| le
    le -.->|"certificat"| certbot
    certbot -.->|"fullchain.pem et privkey.pem"| nginx
    certbot -.->|"fullchain.pem et privkey.pem"| mqtt
    dns -.->|"résout vers l'IP"| nav
```

## Ce que montre le diagramme

| Élément | Détail |
|---|---|
| **Seuls `nginx` et `mqtt` sont joignables depuis Internet** | par les ports 80, 443 et 8883. |
| **`wildfly` n'a aucun port publié** | on y accède uniquement par Nginx. |
| **`mongo` n'écoute que sur `127.0.0.1`** | la base n'est pas exposée ; on s'y connecte par un tunnel SSH. |
| **Un seul certificat** | celui de Let's Encrypt sert à Nginx (HTTPS) et à Mosquitto (TLS). |
| **Aucune image ne quitte le Pi** | seuls l'état des places et les événements partent, en MQTT (*privacy by design*). |

## Ports et protocoles

| Port | Service | Ouvert à Internet | Protocole |
|---|---|---|---|
| 22 | SSH (administration) | oui | SSH, par clé |
| 80 | Nginx | oui | HTTP, redirige vers HTTPS, sert le défi Let's Encrypt |
| 443 | Nginx | oui | HTTPS (TLS 1.2 et 1.3), PWA, `/api`, WebSocket |
| 8883 | Mosquitto | oui | MQTT sur TLS, mot de passe obligatoire, droits par compte (ACL) |
| 8080 | WildFly | **non** | HTTP, réseau Docker seulement |
| 27017 | MongoDB | **non** | protocole MongoDB, `127.0.0.1` seulement |

## Écart connu avec les tests locaux

Sur la VM, MongoDB tourne en version **7.0** (et non 8.0 comme dans `docker-compose.yml`), parce que le noyau Linux 7.0 de la VM empêche MongoDB 8.0 de démarrer. Voir la section 4.4 de [`installation-serveur.md`](../installation-serveur.md).
