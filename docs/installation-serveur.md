# Installation du serveur

Rédaction : Maissa (sprint 1, VM et HTTPS) et Eya (sprint 2, stack Docker sur la VM) · Remplace `docs/installation-vm.md` prévu dans `repartition_travail.md`

> **État actuel (vérifié le 10 novembre 2026)**
>
> | | |
> |---|---|
> | URL | **https://smartparking-supcom.duckdns.org** (la PWA) ; l'API répond sur `/api/sante` |
> | Hébergement | AWS EC2, région **eu-north-1** (Stockholm), instance `smart-parking-vm` |
> | Machine | **t3.small** (2 vCPU, 2 Gio), Ubuntu Server 24.04 LTS, disque 20 Gio gp3, noyau Linux 7.0, swap de 2 Gio |
> | Adresse | Elastic IP 13.49.171.243, nom DNS DuckDNS |
> | Certificat | Let's Encrypt, **expire le 4 février 2027**, renouvelé automatiquement (`certbot.timer`, méthode **webroot**), copié dans `server/certs/` par `certbot-deploy-hook.sh` |
> | Ports ouverts | **22** (SSH), **80** (HTTP → HTTPS), **443** (HTTPS), **8883** (MQTT en TLS) |
> | Logiciels | Docker 29.9 et Compose 5.7 ; 4 conteneurs : `nginx`, `mqtt`, `mongo` (**7.0**, voir 4.4), `wildfly`. Le Nginx de l'hôte est **désactivé** |
> | Vérification | `verifier.sh` : « Tout fonctionne. » (6 contrôles sur 6, le 10 novembre à 22:24 UTC) |

## 0. Ce qui tournait avant, et ce qui tourne depuis le sprint 2

| | **Avant** (sprint 1, 6 novembre) | **Depuis le 10 novembre** (sprint 2) |
|---|---|---|
| Serveur web | Nginx de l'hôte (apt), page par défaut | Conteneur `nginx` de `server/` : PWA + `/api` |
| Certificat | `certbot --nginx` : Certbot modifie lui-même la configuration du Nginx de l'hôte | Même certificat `/etc/letsencrypt/`, copié dans `server/certs/` pour les conteneurs `nginx` et `mqtt` ; renouvellement en mode webroot |
| API, MQTT, base | absents | Conteneurs `wildfly`, `mqtt` (8883), `mongo` |
| Procédure | [section 3](#3-voie-a--vm-aws-procédure-réellement-appliquée) | [section 4](#4-passage-à-la-stack-docker-sur-la-vm-appliqué-le-10-novembre) |

La stack est le même `docker-compose.yml` que celui testé en local :

```
                         ┌──────────────── machine (PC ou VM) ────────────────┐
navigateur ──HTTPS 443──▶│ nginx ──/api──▶ wildfly (api.war)                   │
           ──HTTP 80 ───▶│   │   (sert aussi la PWA)    │                      │
                         │   └─ redirection vers HTTPS  ▼                      │
Raspberry Pi ─MQTT TLS 8883─▶ mqtt (Mosquitto)     mongo (127.0.0.1:27017)    │
                         └────────────────────────────────────────────────────┘
```

| Service | Image | Port ouvert | Rôle |
|---|---|---|---|
| `nginx` | `nginx:1.27-alpine` | 80, 443 | HTTPS, sert la PWA (`frontend/`), transmet `/api` à WildFly |
| `wildfly` | construite depuis `middleware/` | aucun (derrière Nginx) | API Jakarta EE ; sprint 1 : `GET /api/sante` |
| `mqtt` | `eclipse-mosquitto:2` | 8883 | Broker MQTT en TLS, comptes `pi` et `serveur` |
| `mongo` | `mongo:8.0` (**7.0 sur la VM**, voir 4.4) | 27017, **sur 127.0.0.1 seulement** | Base de données, volume persistant `mongo-donnees` |

Deux voies, **avec les mêmes fichiers** :

- **Voie B, locale** : sur le PC, avec un certificat d'une autorité locale → [section 2](#2-voie-b--environnement-local-docker-desktop).
- **Voie A, VM AWS** : HTTPS en place → [section 3](#3-voie-a--vm-aws-procédure-réellement-appliquée), puis la stack Docker (faite le 10 novembre) → [section 4](#4-passage-à-la-stack-docker-sur-la-vm-appliqué-le-10-novembre).

Entre les deux voies, seules changent l'origine des fichiers `server/certs/fullchain.pem` et `privkey.pem`, et la valeur de `DOMAINE` dans `.env`.

---

## 1. Fichiers

```
server/
├── docker-compose.yml          # les 4 services
├── .env.example                # modèle des secrets → copier en .env (jamais commité)
├── nginx/nginx.conf            # HTTPS, redirection 80 → 443, proxy /api et /api/ws
├── mosquitto/
│   ├── mosquitto.conf          # TLS 8883, pas d'anonyme
│   ├── acl                     # droits des comptes pi et serveur (contrat 5.3)
│   └── demarrer.sh             # crée les mots de passe depuis .env puis lance le broker
├── scripts/
│   ├── generer-certificats.sh  # CA locale + certificat serveur (openssl)
│   ├── certbot-deploy-hook.sh  # VM : installe le certificat Let's Encrypt
│   └── verifier.sh             # 6 vérifications automatiques
├── certs/                      # créé par les scripts, ignoré par Git
└── certbot-www/                # VM : défi Let's Encrypt, ignoré par Git
middleware/                     # application Jakarta EE : modules iam, core, api (Dockerfile : Maven puis WildFly)
```

**Secrets** : uniquement dans `server/.env` et `server/certs/`, tous deux dans `.gitignore`. Avant chaque commit, `git status` ne doit montrer ni `.env` ni `.pem`.

---

## 2. Voie B : environnement local (Docker Desktop)

### 2.1 Prérequis

| Outil | Vérifier avec | Résultat attendu |
|---|---|---|
| Docker Desktop (moteur WSL 2) | `docker compose version` | `Docker Compose version v2…` ou plus |
| Git Bash (fourni avec Git) | `openssl version` | `OpenSSL 3.x` |
| Ports 80, 443, 8883 libres | `netstat -ano \| findstr ":443 "` (PowerShell) | aucune ligne `LISTENING` |

Toutes les commandes ci-dessous se tapent dans un terminal **Git Bash** (VS Code : menu du terminal `+ ▾` → *Git Bash*), à la racine du dépôt. **Docker Desktop doit être lancé** (icône de baleine fixe dans la barre des tâches).

### 2.2 Installation

**Étape 1 : créer le fichier des secrets**

```bash
cp server/.env.example server/.env
openssl rand -hex 16      # à lancer 3 fois : un mot de passe par ligne à remplacer
code server/.env           # remplacer les 3 « a_remplacer » ; laisser DOMAINE=localhost
```

Résultat attendu : `grep -c a_remplacer server/.env` affiche `0`.

**Étape 2 : générer les certificats**

```bash
./server/scripts/generer-certificats.sh
```

Résultat attendu :

```
CA locale créée : server/certs/ca.pem
Certificat serveur créé : server/certs/fullchain.pem et server/certs/privkey.pem
Valable pour : DNS:localhost,IP:127.0.0.1,DNS:mqtt
```

> Pour que le **Raspberry Pi** se connecte au broker du PC, ajouter l'adresse IP du PC : `./server/scripts/generer-certificats.sh 192.168.1.20` (adresse donnée par `ipconfig`), puis autoriser le port 8883 dans le pare-feu Windows. Copier `server/certs/ca.pem` sur le Pi (c'est un certificat public, pas un secret).

*Variante avec mkcert* (si vous préférez) : `winget install FiloSottile.mkcert`, `mkcert -install`, puis
`mkcert -cert-file server/certs/fullchain.pem -key-file server/certs/privkey.pem localhost 127.0.0.1 mqtt` et
`cp "$(mkcert -CAROOT)/rootCA.pem" server/certs/ca.pem`.

**Étape 3 (facultative) : faire confiance à la CA locale dans Windows**

Sans cette étape, le navigateur affiche « connexion non privée » (on peut cliquer sur *Continuer*). Avec elle, le cadenas est normal et le service worker de la PWA fonctionne sans avertissement. Dans **PowerShell** :

```powershell
certutil -user -addstore Root server\certs\ca.pem
```

Résultat attendu : une fenêtre de confirmation (cliquer *Oui*), puis `CertUtil: -addstore command completed successfully.` Redémarrer le navigateur. (Pour retirer la CA plus tard : `certmgr.msc` → *Autorités de certification racines de confiance* → *Smart Parking CA locale* → *Supprimer*.)

**Étape 4 : lancer les services**

```bash
cd server
docker compose up -d --build
```

La première fois, il faut 3 à 5 minutes (téléchargement des images, compilation Maven). Résultat attendu à la fin :

```
 Container smartparking-mongo-1 Started
 Container smartparking-mqtt-1 Started
 Container smartparking-wildfly-1 Started
 Container smartparking-nginx-1 Started
```

### 2.3 Vérification

**Automatique** (depuis la racine du dépôt, une trentaine de secondes après le démarrage, le temps que WildFly se lance) :

```bash
./server/scripts/verifier.sh
```

Résultat attendu (sortie réelle du 6 novembre) :

```
1. HTTPS sur https://localhost/
  [OK]    page d'accueil (PWA) : 200
2. Redirection HTTP -> HTTPS
  [OK]    http:// redirige vers https://localhost/
3. API WildFly : GET /api/sante
  [OK]    {"statut":"ok","version":"0.1.0","mongo":"ok","mqtt":"ok","ts":"2026-11-06T20:56:29Z"}
4. MQTT en TLS (port 8883) : publication par 'pi', réception par 'serveur'
  [OK]    message reçu : {"en_ligne":true,"fps":0,"temp_cpu":0}
5. MQTT : connexion anonyme refusée
  [OK]    connexion sans mot de passe refusée
6. MongoDB
  [OK]    ping MongoDB avec authentification

Tout fonctionne.
```

**À la main**, pour comprendre chaque point :

```bash
# État des conteneurs : 4 lignes "Up", mongo "(healthy)"
docker compose -f server/docker-compose.yml ps

# En-têtes HTTPS (sous Windows, --ssl-no-revoke est nécessaire avec une CA locale)
curl -I --ssl-no-revoke --cacert server/certs/ca.pem https://localhost/
```

Résultat attendu :

```
HTTP/1.1 200 OK
Server: nginx
Content-Type: text/html
Strict-Transport-Security: max-age=86400
X-Content-Type-Options: nosniff
...
```

```bash
# API
curl --ssl-no-revoke --cacert server/certs/ca.pem https://localhost/api/sante
# → {"statut":"ok","version":"0.1.0","mongo":"ok","mqtt":"ok","ts":"..."}
```

Test MQTT en TLS avec deux terminaux Git Bash. Les clients Mosquitto sont dans le conteneur, rien à installer. Remplacer `<MDP_…>` par les valeurs de `server/.env`.

```bash
# Terminal 1 : le serveur écoute tout le parking
cd server
MSYS_NO_PATHCONV=1 docker compose exec mqtt mosquitto_sub -h localhost -p 8883 \
  --cafile /mosquitto/certs/ca.pem -u serveur -P <MDP_SERVEUR> -t 'parking/P1/#' -v

# Terminal 2 : le Pi publie l'état d'une place (contrat 5.1)
cd server
MSYS_NO_PATHCONV=1 docker compose exec mqtt mosquitto_pub -h localhost -p 8883 \
  --cafile /mosquitto/certs/ca.pem -u pi -P <MDP_PI> -q 1 \
  -t parking/P1/places/A3 -m '{"place":"A3","etat":"occupee","depuis":"2026-11-20T10:02:11Z"}'
```

Résultat attendu dans le terminal 1 :
`parking/P1/places/A3 {"place":"A3","etat":"occupee","depuis":"2026-11-20T10:02:11Z"}`

Contrôles de sécurité :

| Essai | Résultat attendu |
|---|---|
| `mosquitto_pub` avec `-P faux` | `Connection Refused: not authorised` |
| `mosquitto_pub` sans `--cafile` sur le port 8883 (sans TLS) | `Error: Protocol error` |
| Le compte `pi` publie sur `parking/P1/config` | rien n'arrive chez l'abonné (refus silencieux de l'ACL) |

**Navigateur** : ouvrir `https://localhost` → la PWA s'affiche avec un cadenas (si l'étape 3 est faite) et propose l'installation. `https://localhost/api/sante` affiche le JSON.

**MongoDB** depuis le PC (MongoDB Compass, par exemple) : `mongodb://admin:<MONGO_MOT_DE_PASSE>@localhost:27017/?authSource=admin`.

### 2.4 Arrêter, relancer, tout effacer

```bash
cd server
docker compose stop            # arrête (les données restent)
docker compose start           # relance
docker compose up -d --build   # après une modification du code de middleware/
docker compose logs -f wildfly # suivre les journaux d'un service (Ctrl+C pour sortir)
docker compose down            # supprime les conteneurs (les volumes restent)
docker compose down -v         # ATTENTION : supprime aussi les données MongoDB et MQTT
```

---

## 3. Voie A : VM AWS (procédure réellement appliquée)

Ces étapes ont été suivies le 6 novembre 2026. Les valeurs sont celles de notre VM.

### 3.1 Prérequis

- Un compte AWS. Le nôtre est en plan gratuit, avec 100 USD de crédits valables jusqu'au 6 mai 2027.
- Sur le PC : PowerShell avec `ssh` (inclus dans Windows). Vérifier avec `ssh -V` → `OpenSSH_for_Windows_…`.
- Un compte DuckDNS (connexion avec GitHub ou Google), pour avoir un nom de domaine gratuit.

> Pourquoi DuckDNS : Let's Encrypt **refuse** les noms fournis par AWS (`ec2-….compute.amazonaws.com`). Il faut donc un nom de domaine à nous, et DuckDNS en donne un gratuitement.

### 3.2 Créer l'instance EC2

Console AWS → région **Europe (Stockholm) eu-north-1** (menu en haut à droite) → **EC2** → **Lancer une instance** :

| Champ | Valeur |
|---|---|
| Nom | `smart-parking-vm` |
| Image (AMI) | **Ubuntu Server 24.04 LTS** (x86) |
| Type d'instance | **t3.small** (2 vCPU, 2 Gio) |
| Paire de clés | nouvelle, type RSA, format `.pem` : `smart-parking-key` |
| Réseau | groupe de sécurité créé par l'assistant (`launch-wizard-1`) : autoriser SSH, HTTP et HTTPS depuis Internet |
| Stockage | **20 Gio gp3** |

Le fichier `smart-parking-key.pem` est téléchargé une seule fois. Le ranger dans un dossier personnel **en dehors du dépôt** (noté `<CHEMIN_CLE>` ci-dessous), **jamais dans le dépôt**.

Résultat attendu : l'instance passe à l'état **En cours d'exécution** et les vérifications d'état affichent **2/2**.

### 3.3 Adresse IP fixe (Elastic IP)

Sans Elastic IP, l'adresse publique change à chaque arrêt et redémarrage de la VM.

EC2 → **Adresses IP Elastic** → **Allouer l'adresse IP Elastic** → **Allouer**. Puis **Actions** → **Associer l'adresse IP Elastic** → instance `smart-parking-vm` → **Associer**.

Résultat : la VM a l'adresse **13.49.171.243**.

> Une adresse allouée mais **non associée** est facturée. Une seconde adresse avait été allouée par erreur : elle a été libérée (**Actions** → **Libérer les adresses IP Elastic**). Il ne doit en rester qu'une.

### 3.4 Groupe de sécurité (pare-feu)

EC2 → **Groupes de sécurité** → `launch-wizard-1` → **Modifier les règles entrantes** :

| Type | Port | Source | État |
|---|---|---|---|
| SSH | 22 | 0.0.0.0/0 (mieux : « Mon IP ») | ouvert |
| HTTP | 80 | 0.0.0.0/0 | ouvert |
| HTTPS | 443 | 0.0.0.0/0 | ouvert (ajouté après coup, voir 5.2) |
| TCP personnalisé | 8883 | 0.0.0.0/0 | ouvert le 10 novembre (MQTT en TLS) |

Ne **jamais** ouvrir 27017 (MongoDB) ni 8080 (WildFly).

> **Pour ajouter une règle** : cliquer sur *Ajouter une règle*, **sans modifier ni supprimer les autres**. Le 10 novembre, la règle SSH (22) a disparu au moment d'ajouter le 8883, et l'accès à la VM a été perdu jusqu'à ce qu'elle soit remise (voir 5.2). Après chaque modification, vérifier que les **quatre** lignes ci-dessus sont toujours là.

> Docker contourne le pare-feu `ufw` d'Ubuntu. C'est donc le **groupe de sécurité AWS** qui protège la VM, et MongoDB n'écoute que sur `127.0.0.1`.

### 3.5 Première connexion SSH et mise à jour

Dans PowerShell, sur le PC : protéger la clé (sinon `ssh` la refuse), puis se connecter.

```powershell
icacls "<CHEMIN_CLE>\smart-parking-key.pem" /inheritance:r /grant:r "$($env:USERNAME):R"
ssh -i "<CHEMIN_CLE>\smart-parking-key.pem" ubuntu@13.49.171.243
```

Résultat attendu : la question `Are you sure you want to continue connecting?` (répondre `yes`), puis l'invite `ubuntu@ip-…:~$`. L'utilisateur `ubuntu` n'est pas root : il passe par `sudo`.

Sur la VM :

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y nginx
```

Vérification : `http://13.49.171.243` affiche « Welcome to nginx! ».

### 3.6 Nom de domaine DuckDNS

Sur https://www.duckdns.org : créer le sous-domaine **`smartparking-supcom`**, saisir l'adresse **13.49.171.243** dans *current ip*, puis cliquer sur *update ip*.

> Le **jeton DuckDNS** affiché sur la page est un secret : il permet de changer l'adresse du domaine. Ne jamais le mettre dans le dépôt.

Vérification (PowerShell) :

```powershell
Resolve-DnsName smartparking-supcom.duckdns.org
```

Résultat attendu : une ligne `A` avec `13.49.171.243`.

### 3.7 Nom du serveur dans Nginx

Sur la VM, remplacer `server_name _;` par le domaine dans le site par défaut, tester la configuration, puis recharger :

```bash
sudo sed -i 's/server_name _;/server_name smartparking-supcom.duckdns.org;/' /etc/nginx/sites-available/default
sudo nginx -t && sudo systemctl reload nginx
```

Résultat attendu : `syntax is ok` et `test is successful`. Certbot a besoin de ce `server_name` pour trouver le bon bloc à modifier.

### 3.8 Certificat Let's Encrypt (Certbot, plugin nginx)

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d smartparking-supcom.duckdns.org
```

Certbot demande une adresse email (avertissements d'expiration) et l'acceptation des conditions. Ensuite :
- il obtient le certificat ;
- il ajoute lui-même dans `/etc/nginx/sites-available/default` le bloc `listen 443 ssl` et la redirection de HTTP vers HTTPS (lignes marquées `# managed by Certbot`).

Résultat attendu : `Successfully received certificate` puis `Successfully deployed certificate`.

Fichiers créés :

```
/etc/letsencrypt/live/smartparking-supcom.duckdns.org/fullchain.pem   # certificat + chaîne
/etc/letsencrypt/live/smartparking-supcom.duckdns.org/privkey.pem     # clé privée (reste sur la VM)
```

Le **renouvellement automatique** est assuré par le minuteur `certbot.timer`, installé par le paquet : il passe deux fois par jour et renouvelle le certificat 30 jours avant son expiration.

### 3.9 Vérifications (sorties réelles du 6 novembre 2026)

**Origine des sorties.** Sauf mention contraire, toutes les sorties ci-dessous ont été obtenues par Maissa, avec l'aide de Claude Code, le 6 novembre 2026 entre 23:20 et 23:30 UTC : `curl` et `openssl` depuis le PC, puis une connexion SSH avec des **commandes de lecture uniquement**. Elles sont recopiées sans modification, sauf `certbot certificates`, filtrée sur 5 lignes. Les étapes 3.2 à 3.8 (création de la VM, Elastic IP, DuckDNS, Nginx, Certbot) et le problème du port 443 viennent du compte rendu de Maissa ; ces commandes n'ont pas été rejouées.

**Depuis le PC** (Git Bash ; dans PowerShell, taper `curl.exe` au lieu de `curl`) :

```
$ curl -I https://smartparking-supcom.duckdns.org
HTTP/1.1 200 OK
Server: nginx/1.24.0 (Ubuntu)
Date: Fri, 06 Nov 2026 23:29:42 GMT
Content-Type: text/html
Content-Length: 615
Last-Modified: Fri, 06 Nov 2026 22:03:01 GMT
Connection: keep-alive
ETag: "6aee4e95-267"
Accept-Ranges: bytes
```

```
$ curl -I http://smartparking-supcom.duckdns.org
HTTP/1.1 301 Moved Permanently
Server: nginx/1.24.0 (Ubuntu)
Date: Fri, 06 Nov 2026 23:29:42 GMT
Content-Type: text/html
Content-Length: 178
Connection: keep-alive
Location: https://smartparking-supcom.duckdns.org/
```

Certificat présenté par le serveur :

```
$ openssl s_client -connect smartparking-supcom.duckdns.org:443 -servername smartparking-supcom.duckdns.org </dev/null 2>/dev/null | openssl x509 -noout -subject -issuer -dates
subject=CN=smartparking-supcom.duckdns.org
issuer=C=US, O=Let's Encrypt, CN=YE2
notBefore=Nov  6 21:12:14 2026 GMT
notAfter=Feb  4 21:12:13 2027 GMT
```

**Sur la VM** (en SSH, commandes de lecture seulement) :

```
$ sudo certbot renew --dry-run --no-random-sleep-on-renew
Saving debug log to /var/log/letsencrypt/letsencrypt.log

- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
Processing /etc/letsencrypt/renewal/smartparking-supcom.duckdns.org.conf
- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
Simulating renewal of an existing certificate for smartparking-supcom.duckdns.org

- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
Congratulations, all simulated renewals succeeded:
  /etc/letsencrypt/live/smartparking-supcom.duckdns.org/fullchain.pem (success)
- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
```

> `--no-random-sleep-on-renew` : lancée **sans terminal** (par exemple `ssh … "sudo certbot renew --dry-run"`), la commande attend d'abord un délai aléatoire de quelques minutes. On le voit dans `/var/log/letsencrypt/letsencrypt.log` : `Non-interactive renewal: random delay of 275 seconds`. Ce n'est pas un blocage ; l'option supprime ce délai. Dans une session SSH interactive, il n'y a pas de délai. Le même journal montre aussi un premier `--dry-run` réussi à 22:12 UTC, lancé par Maissa juste après l'installation.

```
$ systemctl list-timers | grep certbot
Sat 2026-11-07 04:01:36 UTC      4h 31min -                                      - certbot.timer                  certbot.service
```

```
$ sudo certbot certificates
  Certificate Name: smartparking-supcom.duckdns.org
    Domains: smartparking-supcom.duckdns.org
    Expiry Date: 2027-02-04 21:12:13+00:00 (VALID: 89 days)
    Certificate Path: /etc/letsencrypt/live/smartparking-supcom.duckdns.org/fullchain.pem
    Private Key Path: /etc/letsencrypt/live/smartparking-supcom.duckdns.org/privkey.pem
```

(Sortie filtrée avec `grep` sur ces cinq lignes.)

```
$ sudo nginx -t
nginx: the configuration file /etc/nginx/nginx.conf syntax is ok
nginx: configuration file /etc/nginx/nginx.conf test is successful
```

```
$ free -h
               total        used        free      shared  buff/cache   available
Mem:           1.9Gi       411Mi       829Mi       2.8Mi       845Mi       1.5Gi
Swap:             0B          0B          0B
```

```
$ df -h
Filesystem       Size  Used Avail Use% Mounted on
/dev/root         19G  2.3G   17G  13% /
tmpfs            953M     0  953M   0% /dev/shm
tmpfs            382M  900K  381M   1% /run
tmpfs            5.0M     0  5.0M   0% /run/lock
efivarfs         128K  3.1K  120K   3% /sys/firmware/efi/efivars
/dev/nvme0n1p16  881M  114M  706M  14% /boot
/dev/nvme0n1p15  105M  6.2M   99M   6% /boot/efi
tmpfs            191M   12K  191M   1% /run/user/1000
```

Autres constats :
- Ubuntu 24.04.5 LTS, 2 vCPU.
- Seuls les ports 22, 80 et 443 sont en écoute (`sudo ss -tlnp`).
- Docker n'est pas installé.
- Le renouvellement passe par le plugin nginx : `/etc/letsencrypt/renewal/smartparking-supcom.duckdns.org.conf` contient `authenticator = nginx` et `installer = nginx`.
- Aucun script de renouvellement (`renewal-hooks`) n'est installé.

| Vérification | Résultat |
|---|---|
| HTTPS répond (`200`) | ✅ |
| HTTP redirige vers HTTPS (`301`) | ✅ |
| Certificat Let's Encrypt valide jusqu'au 4 février 2027 | ✅ |
| Renouvellement simulé (`--dry-run`) | ✅ |
| `certbot.timer` programmé | ✅ |
| Configuration Nginx valide | ✅ |
| Mémoire : 1,5 Gio disponible, **pas de swap** | ⚠️ suffisant aujourd'hui ; à corriger avant la stack Docker (4.2) |
| Disque : 17 Gio libres sur 19 | ✅ |
| Port 8883 joignable | ⏳ fermé, prévu au sprint 2 |

### 3.10 Coût et arrêt de la VM

Ordres de grandeur à eu-north-1 (à vérifier dans AWS *Billing and Cost Management*) :

| Élément | Facturé | Prix approximatif |
|---|---|---|
| t3.small | seulement quand l'instance **tourne** | ~0,022 USD/h, soit ~16 USD/mois en continu |
| Disque gp3 20 Gio | toujours, même instance arrêtée | ~2 USD/mois |
| Adresse IPv4 publique (Elastic IP) | toujours, même instance arrêtée | ~3,6 USD/mois |
| **Total** | VM allumée en continu | **~20 USD/mois**, pris sur les crédits |

Allumée en permanence, la VM coûterait environ 120 USD d'ici au 6 mai 2027, **plus que les 100 USD de crédits**. Il faut donc l'**arrêter quand elle ne sert pas pendant plusieurs jours**. Arrêtée, elle ne coûte plus qu'environ 5,6 USD/mois (disque et IP).

**Arrêter** : EC2 → *Instances* → `smart-parking-vm` → **État de l'instance** → **Arrêter l'instance**. Ne **pas** choisir *Résilier* : cela supprime la VM et son disque.

**Redémarrer** : **État de l'instance** → **Démarrer l'instance**. L'Elastic IP et le nom DuckDNS ne changent pas, et Nginx redémarre seul. Compter 1 à 2 minutes avant que `https://` réponde.

**Surveiller les crédits** :
- *Billing and Cost Management* → *Credits* : le solde restant ;
- *Budgets* → *Create budget* → modèle *Monthly cost budget*, avec une alerte par email à 50 % et 80 %.

> En plan gratuit, vérifier dans *Billing* ce qui se passe quand les crédits sont épuisés ou expirés. La clôture du compte peut entraîner la suppression des ressources.

**Fin du projet** : résilier l'instance, puis **libérer l'Elastic IP** et supprimer les instantanés éventuels.

---

## 4. Passage à la stack Docker sur la VM (appliqué le 10 novembre)

> **Appliqué le 10 novembre 2026** par Eya (sprint 2, profil A). Les étapes de 4.2 sont celles réellement suivies, avec trois écarts par rapport au plan initial, décrits en 4.4 (MongoDB 7.0, construction avant la coupure, `bash` pour `verifier.sh`).

### 4.1 Le problème, et l'option retenue

Le Nginx de l'hôte occupe déjà les ports 80 et 443, et le conteneur `nginx` de `server/` les demande aussi : les deux ne peuvent pas tourner en même temps.

**Option retenue : arrêter le Nginx de l'hôte et laisser le conteneur `nginx` servir les ports 80 et 443, avec le certificat de `/etc/letsencrypt/` copié dans `server/certs/` par `certbot-deploy-hook.sh`.**

Pourquoi :
- La VM utilise exactement la configuration déjà testée en local (`server/nginx/nginx.conf`) : une seule configuration Nginx à maintenir.
- Le même certificat sert aussi à Mosquitto sur le port 8883.
- Garder le Nginx de l'hôte en reverse proxy devant le conteneur ajouterait une deuxième configuration, obligerait le conteneur à écouter sur d'autres ports et ferait diverger la VM de l'environnement local.

Conséquence à ne pas oublier : le renouvellement passe aujourd'hui par le **plugin nginx** de Certbot, qui a besoin du Nginx de l'hôte. Il faut le basculer en mode **webroot**. Le conteneur `nginx` sert déjà le dossier du défi (`/.well-known/acme-challenge/` → `server/certbot-www/`).

### 4.2 Étapes suivies

1. **Swap de 2 Gio** : la VM n'en a pas, et WildFly avec MongoDB peut dépasser 2 Gio.
   ```bash
   sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
   sudo mkswap /swapfile && sudo swapon /swapfile
   echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
   free -h        # → Swap: 2.0Gi
   ```
2. **Installer Docker** (dépôt officiel), puis se reconnecter :
   ```bash
   sudo apt-get install -y ca-certificates curl git
   sudo install -m 0755 -d /etc/apt/keyrings
   sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
   sudo chmod a+r /etc/apt/keyrings/docker.asc
   echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
     | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
   sudo apt-get update
   sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
   sudo usermod -aG docker $USER && exit
   ```
   Vérification après reconnexion : `docker run --rm hello-world` → `Hello from Docker!`
3. **Récupérer le projet et créer `.env`** :
   ```bash
   sudo mkdir -p /opt/smartparking && sudo chown $USER: /opt/smartparking
   git clone https://github.com/amaltrigui19/projet_smart_parking.git /opt/smartparking
   cd /opt/smartparking/server
   cp .env.example .env && chmod 600 .env
   sed -i 's/^DOMAINE=.*/DOMAINE=smartparking-supcom.duckdns.org/' .env
   sed -i "s/^MONGO_MOT_DE_PASSE=.*/MONGO_MOT_DE_PASSE=$(openssl rand -hex 16)/" .env
   sed -i "s/^MQTT_MDP_PI=.*/MQTT_MDP_PI=$(openssl rand -hex 16)/" .env
   sed -i "s/^MQTT_MDP_SERVEUR=.*/MQTT_MDP_SERVEUR=$(openssl rand -hex 16)/" .env
   ```
   Les mots de passe sont générés et écrits directement dans le fichier : ils ne s'affichent pas à l'écran. Vérification sans les montrer : `grep -c a_remplacer .env` → `0`, et `grep -E '^(MONGO_MOT_DE_PASSE|MQTT_MDP_PI|MQTT_MDP_SERVEUR)=.{32}$' .env | wc -l` → `3`. Le mot de passe `MQTT_MDP_PI` est à transmettre à la personne qui configure le Raspberry Pi, **par un canal privé** ; les deux autres ne quittent jamais la VM.
4. **Installer le script de copie du certificat et le lancer une fois.** Il copie `/etc/letsencrypt/live/smartparking-supcom.duckdns.org/{fullchain,privkey}.pem` dans `server/certs/`. Comme les conteneurs ne tournent pas encore, il ne recharge rien.
   ```bash
   sudo cp scripts/certbot-deploy-hook.sh /etc/letsencrypt/renewal-hooks/deploy/smartparking.sh
   sudo chmod 755 /etc/letsencrypt/renewal-hooks/deploy/smartparking.sh
   sudo RENEWED_LINEAGE=/etc/letsencrypt/live/smartparking-supcom.duckdns.org \
        /etc/letsencrypt/renewal-hooks/deploy/smartparking.sh
   ls -l certs/   # → fullchain.pem (644) et privkey.pem (640, groupe 1883)
   ```
5. **Basculer du Nginx de l'hôte au conteneur.** On télécharge et on construit **avant** de couper le site, qui n'est alors indisponible que quelques secondes.
   ```bash
   docker compose pull --ignore-buildable    # images nginx, mosquitto, mongo
   docker compose build                      # image WildFly (Maven, 3 à 6 minutes la première fois)
   ```
   Créer le fichier local qui impose MongoDB 7.0 (voir 4.4), puis couper l'ancien Nginx et démarrer :
   ```bash
   cat > docker-compose.override.yml <<'EOF'
   # Spécifique à la VM : MongoDB 8.0 refuse de démarrer sur le noyau Linux 7.0 (SERVER-121912).
   services:
     mongo:
       image: mongo:7.0
   EOF
   docker compose config --images | grep mongo   # → mongo:7.0
   sudo systemctl disable --now nginx
   docker compose up -d
   docker compose ps                     # 4 services "Up", mongo "(healthy)"
   ```
6. **Basculer le renouvellement en mode webroot**, puis vérifier :
   ```bash
   sudo certbot reconfigure --cert-name smartparking-supcom.duckdns.org \
        --webroot -w /opt/smartparking/server/certbot-www
   sudo grep -E 'authenticator|installer' /etc/letsencrypt/renewal/smartparking-supcom.duckdns.org.conf
   # attendu : authenticator = webroot (et plus d'installer = nginx)
   sudo certbot renew --dry-run --no-random-sleep-on-renew
   # attendu : Congratulations, all simulated renewals succeeded
   ```
   La ligne `installer = nginx` est **restée** dans le fichier le 10 novembre : la retirer (avec une sauvegarde), puis relancer le `--dry-run` :
   ```bash
   F=/etc/letsencrypt/renewal/smartparking-supcom.duckdns.org.conf
   sudo cp $F $F.bak
   sudo sed -i '/^installer = nginx/d' $F
   sudo certbot renew --dry-run --no-random-sleep-on-renew
   ```
7. **Ouvrir le port 8883** dans le groupe de sécurité `launch-wizard-1` (règle TCP 8883, source 0.0.0.0/0). **Ajouter** la règle sans modifier les autres, puis vérifier que SSH (22), HTTP (80) et HTTPS (443) y sont toujours (voir 3.4 et 5.2). Test depuis le PC : `Test-NetConnection smartparking-supcom.duckdns.org -Port 8883` → `TcpTestSucceeded : True`.
8. **Tout vérifier** :
   ```bash
   sudo apt-get install -y mosquitto-clients
   bash /opt/smartparking/server/scripts/verifier.sh smartparking-supcom.duckdns.org   # → "Tout fonctionne."
   ```
   On lance le script avec `bash` : après un `git clone` depuis Windows, il n'a pas le droit d'exécution (`Permission denied` avec `./`).
   Depuis le PC : `curl -I https://smartparking-supcom.duckdns.org` → `200`, et https://smartparking-supcom.duckdns.org/api/sante → `{"statut":"ok",...}`.

**Retour en arrière** si quelque chose ne marche pas : `docker compose down`, puis `sudo systemctl enable --now nginx`. Le Nginx de l'hôte n'a pas été modifié.

### 4.3 MQTT (port 8883) avec le certificat Let's Encrypt

- Mosquitto lit `server/certs/fullchain.pem` et `privkey.pem`. Ce sont les mêmes fichiers que Nginx : il n'y a **pas de second certificat** à demander.
- La clé est lisible par le groupe 1883, l'utilisateur `mosquitto` du conteneur : `certbot-deploy-hook.sh` s'en charge.
- À chaque renouvellement, le script recopie les fichiers, recharge Nginx et redémarre Mosquitto.
- **Côté Raspberry Pi** : Let's Encrypt est reconnu par le système. On se connecte donc avec `--capath /etc/ssl/certs`, ou `tls_set()` sans argument en Python, sur `smartparking-supcom.duckdns.org:8883`. Aucun fichier de certificat n'est à copier sur le Pi.
- **Sans Raspberry Pi** : le compte `pi` n'est qu'un nom et un mot de passe. N'importe quel poste peut jouer son rôle, et c'est ce que fait `verifier.sh` (contrôle 4).

### 4.4 Écarts par rapport au plan initial, et problèmes rencontrés (10 novembre)

| Sujet | Ce qui s'est passé | Ce qu'on a fait |
|---|---|---|
| **MongoDB 8.0 refuse de démarrer** | Le conteneur `mongo` redémarrait en boucle (`Restarting (1)`). Journal : « MongoDB cannot start: Linux kernel versions 6.19 and newer has a known incompatibility with this version of MongoDB » (fiche SERVER-121912, qui parle de « MongoDB 8.0+ »). Le noyau de la VM est le `7.0.0-1014-aws`. En local, Docker Desktop utilise un noyau plus ancien : d'où l'écart avec les tests de Maissa. | Fichier local **`server/docker-compose.override.yml`** qui impose `mongo:7.0` (Compose le charge tout seul). Il n'est **pas** dans Git : le problème ne concerne que le noyau de la VM. Si une base 8.0 a commencé à s'initialiser, supprimer d'abord son volume (`docker volume rm smartparking_mongo-donnees`) : une 7.0 ne peut pas ouvrir une base commencée par une 8.0. **À revoir** : si une version 8.x corrigée sort, retirer ce fichier. |
| **Construction avant la coupure** | `docker compose up -d --build` aurait coupé le site pendant toute la construction (plusieurs minutes). | `pull` puis `build` pendant que l'ancien Nginx répond ; la coupure ne dure que le temps de `disable --now nginx` et `up -d`. |
| **`verifier.sh` : `Permission denied`** | Les scripts n'ont pas le droit d'exécution après un clone depuis Windows. | Lancer avec `bash script.sh`. À corriger dans le dépôt (`git update-index --chmod=+x`) pour que `./script.sh` marche. |
| **`installer = nginx` resté dans le renouvellement Certbot** | `certbot reconfigure` change la méthode (`webroot`) mais pas l'installateur. | Ligne supprimée (avec sauvegarde `.bak`), puis simulation de renouvellement réussie. |
| **SSH coupé après l'ajout du port 8883** | La règle SSH (22) n'était plus dans le groupe de sécurité : `ssh` répondait `Connection timed out`, alors que le 443 passait. | Règle SSH (22, `0.0.0.0/0`) rajoutée. Voir 3.4 et 5.2. |

**Ressources mesurées** avec les 4 conteneurs au repos : 964 Mo de mémoire utilisés sur 1,9 Go, 941 Mo disponibles, swap à 64 Ko sur 2 Go. Suffisant aujourd'hui ; à surveiller quand l'API fera du vrai travail.

**Tenue au redémarrage** (vérifiée en lecture le 10 novembre) : Docker est `enabled`, le Nginx de l'hôte est `disabled`, le swap est actif (`swapon --show` : `/swapfile`, 2 Go ; sa ligne est ajoutée à `/etc/fstab` par l'étape 1) et les services du `docker-compose.yml` ont `restart: unless-stopped`. Un vrai redémarrage de la VM n'a pas été testé.

### 4.5 Sortie du contrôle final

Sortie de `verifier.sh` sur la VM, recopiée le 10 novembre 2026 :

```
1. HTTPS sur https://smartparking-supcom.duckdns.org/
  [OK]    page d'accueil (PWA) : 200
2. Redirection HTTP -> HTTPS
  [OK]    http:// redirige vers https://smartparking-supcom.duckdns.org/
3. API WildFly : GET /api/sante
  [OK]    {"statut":"ok","version":"0.1.0","mongo":"ok","mqtt":"ok","ts":"2026-11-10T22:24:55Z"}
4. MQTT en TLS (port 8883) : publication par 'pi', réception par 'serveur'
  [OK]    message reçu : {"en_ligne":true,"fps":0,"temp_cpu":0}
5. MQTT : connexion anonyme refusée
  [OK]    connexion sans mot de passe refusée
6. MongoDB
  [OK]    ping MongoDB avec authentification

Tout fonctionne.
```

---

## 5. Dépannage

### 5.1 Environnement local (voie B)

| Symptôme | Cause | Solution |
|---|---|---|
| `failed to connect to the docker API … dockerDesktopLinuxEngine` | Docker Desktop n'est pas lancé | Lancer Docker Desktop, attendre que la baleine ne bouge plus |
| Le navigateur affiche « Votre connexion n'est pas privée » sur https://localhost | La CA locale n'est pas reconnue par Windows | `certutil -user -addstore Root server\certs\ca.pem`, puis fermer et rouvrir le navigateur (étape 3 de 2.2) |
| `curl: (60) schannel: … CERT_TRUST_REVOCATION_STATUS_UNKNOWN` (Windows) | Le curl de Windows cherche une liste de révocation que la CA locale n'a pas | Ajouter `--ssl-no-revoke` (fait par `verifier.sh`) |
| En PowerShell, `curl -I` donne une erreur de paramètre | `curl` = `Invoke-WebRequest` dans PowerShell 5 | Taper `curl.exe` |
| `Error: Problem setting TLS options: File not found` avec `docker compose exec` dans Git Bash | Git Bash transforme `/mosquitto/...` en chemin Windows | Préfixer par `MSYS_NO_PATHCONV=1` |
| `Bind for 0.0.0.0:443 failed: port is already allocated` | Un autre programme utilise 80, 443 ou 8883 | `netstat -ano \| findstr ":443 "` puis arrêter ce programme |
| Nginx s'arrête : `cannot load certificate "/etc/nginx/certs/fullchain.pem"` | Certificats absents | `./server/scripts/generer-certificats.sh` puis `docker compose restart nginx` |
| `502 Bad Gateway` sur `/api/...` | WildFly démarre encore (10 à 30 s) ou a planté | Attendre, puis `docker compose logs wildfly` |
| `/api/sante` répond `503` avec `"mongo":"ko"` ou `"mqtt":"ko"` | Le service indiqué ne tourne pas | `docker compose ps`, puis `docker compose logs mongo` (ou `mqtt`) |
| `Connection Refused: not authorised` (MQTT) | Mauvais mot de passe, ou `.env` modifié sans recréer le conteneur | Vérifier `.env`, puis `docker compose up -d --force-recreate mqtt` |
| Un message MQTT publié n'arrive jamais | L'ACL interdit ce topic à ce compte (refus silencieux) | Vérifier `server/mosquitto/acl` et le contrat 5.3 |
| Journaux de `mqtt` : `unexpected eof while reading` venant de WildFly | Normal : `/api/sante` vérifie seulement que le port 8883 répond, sans faire la négociation TLS | Rien à faire |
| MongoDB refuse le nouveau mot de passe mis dans `.env` | Le compte admin n'est créé qu'au **premier** démarrage, sur un volume vide | Changer le mot de passe dans `mongosh`, ou `docker compose down -v` (efface les données) |
| `$'\r': command not found` | Script enregistré avec des fins de ligne Windows (CRLF) | `.gitattributes` force LF ; sinon `sed -i 's/\r$//' <fichier>` |

### 5.2 VM AWS (voie A)

| Symptôme | Cause | Solution |
|---|---|---|
| `https://` donne `ERR_CONNECTION_TIMED_OUT` alors que `http://` marche | **Port 443 absent du groupe de sécurité** : Nginx écoute bien, mais AWS bloque le trafic. C'est le problème rencontré le 6 novembre | Groupe de sécurité → règle entrante **HTTPS 443**, source 0.0.0.0/0 |
| MQTT : `Connection timed out` sur le port 8883 | La règle TCP 8883 manque dans le groupe de sécurité | Ajouter la règle (étape 7 de 4.2) ; test : `Test-NetConnection <domaine> -Port 8883` |
| `ssh` : `UNPROTECTED PRIVATE KEY FILE` ou `Permission denied (publickey)` | Droits trop larges sur la clé, ou mauvais utilisateur | Commande `icacls` de 3.5 ; l'utilisateur est `ubuntu` |
| `ssh` : `Connection timed out` | Instance arrêtée, **règle SSH (22) absente ou restreinte à une autre IP**, ou réseau qui bloque la sortie vers le port 22 | `Test-NetConnection <ip> -Port 22` (échec) et `-Port 443` (succès) → c'est le groupe de sécurité : ajouter une règle SSH, 22, `0.0.0.0/0`. Si la règle est bonne : essayer un autre réseau (partage de connexion du téléphone). C'est arrivé le 10 novembre après l'ajout du 8883 |
| Le domaine ne pointe pas vers la VM | Adresse DuckDNS fausse, ou Elastic IP dissociée | `Resolve-DnsName smartparking-supcom.duckdns.org` → doit donner `13.49.171.243` ; corriger sur duckdns.org |
| Certbot : `Timeout during connect` ou `unauthorized` | Port 80 fermé, domaine faux, ou serveur web arrêté | Vérifier 3.4, le DNS (3.6) et `sudo systemctl status nginx` |
| Certbot : `too many failed authorizations` | Limite de Let's Encrypt (5 échecs par heure) | Attendre 1 heure ; s'entraîner avec `--staging` |
| `sudo certbot renew --dry-run` semble bloqué quand il est lancé à distance | Délai aléatoire normal sans terminal (voir 3.9) | Attendre, ou ajouter `--no-random-sleep-on-renew` |
| Commande envoyée par `ssh` depuis **PowerShell 5** cassée (`command not found` sur un morceau de motif `grep`) | PowerShell retire les guillemets doubles des arguments passés à `ssh` | Se connecter d'abord (`ssh …`), puis taper la commande sur la VM ; ou utiliser Git Bash |
| Après le passage à Docker (4.2), le renouvellement échoue | Certbot utilise encore le plugin nginx de l'hôte | Étape 6 de 4.2 (`certbot reconfigure … --webroot`) |
| Après le passage à Docker, `Bind for 0.0.0.0:80 failed` | Le Nginx de l'hôte tourne encore | `sudo systemctl disable --now nginx` |
| La VM rame, WildFly redémarre seul | Mémoire pleine (2 Gio, sans swap) | Créer le swap (étape 1 de 4.2) ; `docker stats` |
| `mongo` en `Restarting (1)` ; journal : `MongoDB cannot start: Linux kernel versions 6.19 and newer ...` | MongoDB 8.0 incompatible avec le noyau 6.19 ou plus récent (SERVER-121912) | `docker-compose.override.yml` avec `mongo:7.0` (voir 4.4), après avoir supprimé le volume vide `smartparking_mongo-donnees` |
| `./server/scripts/verifier.sh` : `Permission denied` | Le script n'a pas le droit d'exécution après un clone depuis Windows | `bash server/scripts/verifier.sh <domaine>` |
---

## Annexe : alternative Azure

Si l'équipe passe un jour sur Azure, les étapes sont les mêmes, avec ces différences :
- VM **Standard_B1ms** (2 Go) avec Ubuntu 24.04 et IP publique statique.
- Nom DNS gratuit : *IP publique* → *Configuration* → *Étiquette du nom DNS* (`<nom>.<region>.cloudapp.azure.com`). Let's Encrypt l'accepte, DuckDNS n'est donc pas nécessaire.
- Utilisateur `azureuser`.
- Ports à ouvrir dans le *groupe de sécurité réseau*.
- Pour arrêter la VM, choisir **Arrêter** dans le portail, jusqu'à l'état « Arrêté (désalloué) ». Un `shutdown` lancé depuis la VM ne suffit pas : elle reste facturée.
