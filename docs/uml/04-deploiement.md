# Diagramme de déploiement

Source : [`server/docker-compose.yml`](../../server/docker-compose.yml), [`docs/installation-serveur.md`](../installation-serveur.md) et cahier des charges. L'état décrit est celui **vérifié le 10 novembre 2026** sur la VM.

```mermaid
flowchart LR
    subgraph maquette ["«device» Maquette du parking (sur site)"]
        direction TB
        cam["«device» Caméra<br/>module Pi ou webcam USB"]
        pi["«device» Raspberry Pi 4<br/>«artifact» script de détection Python<br/>OpenCV, YOLO11n, client MQTT"]
        cam -->|"images (jamais envoyées)"| pi
    end

    subgraph vm ["«device» VM AWS EC2 t3.small, Ubuntu 24.04, Docker Compose<br/>smartparking-supcom.duckdns.org, IP 13.49.171.243"]
        direction TB
        subgraph docker ["«executionEnvironment» Docker"]
            direction TB
            nginx["«container» nginx<br/>ports 80 et 443<br/>HTTPS, HSTS, proxy vers WildFly"]
            wildfly["«executionEnvironment» WildFly<br/>port 8080, interne<br/>«artifact» ROOT.war : front office / et back office /admin/<br/>«artifact» api.war : /api (modules iam, core, api)"]
            mqtt["«container» Mosquitto<br/>port 8883, TLS<br/>comptes pi et serveur"]
            mongo["«container» MongoDB<br/>port 27017<br/>sur 127.0.0.1 seulement"]
        end
        certbot["«executionEnvironment» Certbot sur l'hôte<br/>renouvellement webroot<br/>copie vers server/certs"]
    end

    nav["«device» Navigateur ou mobile<br/>PWA : conducteur et exploitant"]
    le["Let's Encrypt<br/>certificat HTTPS et TLS"]
    dns["DuckDNS<br/>nom de domaine"]
    push["Service de push<br/>Google, Mozilla, Apple"]

    pi -->|"MQTT sur TLS, 8883"| mqtt
    nav -->|"HTTPS 443, JWT"| nginx
    nav -.->|"HTTP 80, redirigé vers HTTPS"| nginx
    nginx -->|"proxy /, /admin/, /api et /api/ws"| wildfly
    wildfly -->|"MongoDB, authentifié"| mongo
    wildfly -->|"abonné à parking/P1/#, publie parking/P1/config"| mqtt
    wildfly -.->|"Web Push, clés VAPID"| push
    push -.->|"notification"| nav
    certbot -.->|"demande de certificat (ACME, HTTPS)"| le
    le -.->|"vérifie le défi HTTP-01 (port 80)"| nginx
    certbot -.->|"écrit le défi dans certbot-www"| nginx
    le -.->|"certificat"| certbot
    certbot -.->|"fullchain.pem et privkey.pem"| nginx
    certbot -.->|"fullchain.pem et privkey.pem"| mqtt
    nav -.->|"résolution du nom"| dns
```

Notation : Mermaid n'a pas de diagramme de déploiement UML (nœuds en 3D). Les stéréotypes UML sont donc écrits dans les étiquettes : `«device»` pour une machine physique ou virtuelle, `«executionEnvironment»` pour un environnement d'exécution, `«artifact»` pour ce qui y est déployé.

## Ce que montre le diagramme

| Élément | Détail |
|---|---|
| **Seuls `nginx` et `mqtt` sont joignables depuis Internet** | par les ports 80, 443 et 8883. |
| **`wildfly` n'a aucun port publié** | on y accède uniquement par Nginx. Il sert les deux PWA (`ROOT.war`) et l'API (`api.war`) ; Nginx ne fait que le HTTPS (voir [`installation-serveur.md`](../installation-serveur.md) §6.1). |
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