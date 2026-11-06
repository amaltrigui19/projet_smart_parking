# Installation du serveur

Rédaction : Maissa · Sprint 1 · Remplace `docs/installation-vm.md` prévu dans `repartition_travail.md`

Le serveur regroupe 4 services, lancés par **un seul fichier** [`infra/docker-compose.yml`](../infra/docker-compose.yml) :

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
| `nginx` | `nginx:1.27-alpine` | 80, 443 | HTTPS, sert la PWA (`pwa/`), transmet `/api` à WildFly |
| `wildfly` | construite depuis `api/` | aucun (derrière Nginx) | API Jakarta EE ; sprint 1 : `GET /api/sante` |
| `mqtt` | `eclipse-mosquitto:2` | 8883 | Broker MQTT en TLS, comptes `pi` et `serveur` |
| `mongo` | `mongo:8.0` | 27017, **sur 127.0.0.1 seulement** | Base de données, volume persistant `mongo-donnees` |

Deux voies, **avec les mêmes fichiers** :

- **Voie B, locale** (en attendant l'accès au cloud) : sur le PC, certificat d'une autorité locale → [section 2](#2-voie-b--environnement-local-docker-desktop).
- **Voie A, VM cloud** (cible finale) : Ubuntu 24.04, nom DNS, certificat Let's Encrypt → [section 3](#3-voie-a--vm-cloud-ubuntu-2404).

La seule différence entre les deux est l'origine des fichiers `infra/certs/fullchain.pem` et `privkey.pem`, et la valeur de `DOMAINE` dans `.env`.

---

## 1. Fichiers

```
infra/
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
api/                            # application Jakarta EE (Dockerfile : Maven puis WildFly)
```

**Secrets** : uniquement dans `infra/.env` et `infra/certs/`, tous deux dans `.gitignore`. Avant chaque commit, `git status` ne doit montrer ni `.env` ni `.pem`.

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
cp infra/.env.example infra/.env
openssl rand -hex 16      # à lancer 3 fois : un mot de passe par ligne à remplacer
code infra/.env           # remplacer les 3 « a_remplacer » ; laisser DOMAINE=localhost
```

Résultat attendu : `grep -c a_remplacer infra/.env` affiche `0`.

**Étape 2 : générer les certificats**

```bash
./infra/scripts/generer-certificats.sh
```

Résultat attendu :

```
CA locale créée : infra/certs/ca.pem
Certificat serveur créé : infra/certs/fullchain.pem et infra/certs/privkey.pem
Valable pour : DNS:localhost,IP:127.0.0.1,DNS:mqtt
```

> Pour que le **Raspberry Pi** se connecte au broker du PC, ajouter l'adresse IP du PC : `./infra/scripts/generer-certificats.sh 192.168.1.20` (adresse donnée par `ipconfig`), puis autoriser le port 8883 dans le pare-feu Windows. Copier `infra/certs/ca.pem` sur le Pi (c'est un certificat public, pas un secret).

*Variante avec mkcert* (si vous préférez) : `winget install FiloSottile.mkcert`, `mkcert -install`, puis
`mkcert -cert-file infra/certs/fullchain.pem -key-file infra/certs/privkey.pem localhost 127.0.0.1 mqtt` et
`cp "$(mkcert -CAROOT)/rootCA.pem" infra/certs/ca.pem`.

**Étape 3 (facultative) : faire confiance à la CA locale dans Windows**

Sans cette étape, le navigateur affiche « connexion non privée » (on peut cliquer sur *Continuer*). Avec elle, le cadenas est normal et le service worker de la PWA fonctionne sans avertissement. Dans **PowerShell** :

```powershell
certutil -user -addstore Root infra\certs\ca.pem
```

Résultat attendu : une fenêtre de confirmation (cliquer *Oui*), puis `CertUtil: -addstore command completed successfully.` Redémarrer le navigateur. (Pour retirer la CA plus tard : `certmgr.msc` → *Autorités de certification racines de confiance* → *Smart Parking CA locale* → *Supprimer*.)

**Étape 4 : lancer les services**

```bash
cd infra
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
./infra/scripts/verifier.sh
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
docker compose -f infra/docker-compose.yml ps

# En-têtes HTTPS (sous Windows, --ssl-no-revoke est nécessaire avec une CA locale)
curl -I --ssl-no-revoke --cacert infra/certs/ca.pem https://localhost/
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
curl --ssl-no-revoke --cacert infra/certs/ca.pem https://localhost/api/sante
# → {"statut":"ok","version":"0.1.0","mongo":"ok","mqtt":"ok","ts":"..."}
```

Test MQTT en TLS avec deux terminaux Git Bash. Les clients Mosquitto sont dans le conteneur, rien à installer. Remplacer `<MDP_…>` par les valeurs de `infra/.env`.

```bash
# Terminal 1 : le serveur écoute tout le parking
cd infra
MSYS_NO_PATHCONV=1 docker compose exec mqtt mosquitto_sub -h localhost -p 8883 \
  --cafile /mosquitto/certs/ca.pem -u serveur -P <MDP_SERVEUR> -t 'parking/P1/#' -v

# Terminal 2 : le Pi publie l'état d'une place (contrat 5.1)
cd infra
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
cd infra
docker compose stop            # arrête (les données restent)
docker compose start           # relance
docker compose up -d --build   # après une modification du code de api/
docker compose logs -f wildfly # suivre les journaux d'un service (Ctrl+C pour sortir)
docker compose down            # supprime les conteneurs (les volumes restent)
docker compose down -v         # ATTENTION : supprime aussi les données MongoDB et MQTT
```

---

## 3. Voie A : VM cloud (Ubuntu 24.04)

Les exemples utilisent **Azure** (nom DNS gratuit `*.cloudapp.azure.com`). Les différences pour AWS sont en 3.9.
Dans les commandes, remplacer `smartparking-supcom.francecentral.cloudapp.azure.com` par votre nom DNS. Pour éviter de le retaper, on le met dans une variable du terminal :

```bash
DOMAINE=smartparking-supcom.francecentral.cloudapp.azure.com
```

### 3.1 Prérequis

- Un abonnement Azure (Azure for Students : 100 USD de crédit, sans carte bancaire) ou AWS.
- Sur le PC : PowerShell avec `ssh` (inclus dans Windows 10 et 11) ; vérifier avec `ssh -V` → `OpenSSH_for_Windows_…`.
- Une adresse email pour Let's Encrypt (avertissements d'expiration).

### 3.2 Créer la VM (portail Azure)

*Créer une ressource* → *Machine virtuelle* :

| Champ | Valeur |
|---|---|
| Groupe de ressources | `rg-smartparking` (nouveau) |
| Nom | `vm-smartparking` |
| Région | une région autorisée par l'abonnement étudiant, proche de la Tunisie (`France Central`, `West Europe`, `Italy North`) |
| Image | **Ubuntu Server 24.04 LTS** (x64) |
| Taille | **Standard_B1ms** (1 vCPU, 2 Go) ; Standard_B2s (2 vCPU, 4 Go) si WildFly manque de mémoire |
| Authentification | **Clé publique SSH**, utilisateur `azureuser`, nouvelle paire de clés `vm-smartparking_key` |
| Ports d'entrée publics | SSH (22) (on ajoute les autres en 3.3) |
| Disque | SSD Standard, 30 Go |
| Réseau → IP publique | nouvelle, **SKU Standard, statique** |

*Vérifier + créer* → *Créer* → **télécharger la clé privée** `vm-smartparking_key.pem` et la ranger dans `C:\Users\<vous>\.ssh\`. Ne jamais la mettre dans le dépôt.

**Nom DNS** : ressource *IP publique* de la VM → *Configuration* → *Étiquette du nom DNS* = `smartparking-supcom` → *Enregistrer*.
Résultat : le nom `smartparking-supcom.<region>.cloudapp.azure.com` apparaît dans la page *Vue d'ensemble* de la VM.

Vérification depuis PowerShell :

```powershell
Resolve-DnsName smartparking-supcom.francecentral.cloudapp.azure.com
```

Résultat attendu : une ligne `A` avec l'adresse IP publique de la VM.

*Variante en ligne de commande* (Azure CLI, `winget install Microsoft.AzureCLI`, puis `az login`) :

```bash
az group create -n rg-smartparking -l francecentral
az vm create -g rg-smartparking -n vm-smartparking --image Ubuntu2404 \
  --size Standard_B1ms --admin-username azureuser --generate-ssh-keys \
  --public-ip-sku Standard --public-ip-address-dns-name smartparking-supcom \
  --os-disk-size-gb 30
```

### 3.3 Ouvrir les ports

VM → *Réseau* → *Paramètres réseau* → *Créer une règle de port* → *Règle de port d'entrée* :

| Priorité | Nom | Port | Source | Pourquoi |
|---|---|---|---|---|
| 300 | SSH | 22 | **Mon adresse IP** (de préférence) | Administration |
| 310 | HTTP | 80 | Tous | Défi Let's Encrypt, redirection vers HTTPS |
| 320 | HTTPS | 443 | Tous | PWA et API |
| 330 | MQTTS | 8883 | Tous | Raspberry Pi |

Ne **pas** ouvrir 27017 (MongoDB) ni 8080 (WildFly). En CLI :

```bash
az vm open-port -g rg-smartparking -n vm-smartparking --port 80,443,8883 --priority 310
```

> Docker contourne le pare-feu `ufw` d'Ubuntu : c'est le **groupe de sécurité réseau Azure** (ou le *security group* AWS) qui protège la VM. C'est aussi pour cela que MongoDB n'écoute que sur `127.0.0.1`.

### 3.4 Première connexion et préparation du système

Sur le PC (PowerShell), protéger la clé (sinon `ssh` la refuse), puis se connecter :

```powershell
icacls $HOME\.ssh\vm-smartparking_key.pem /inheritance:r /grant:r "$($env:USERNAME):R"
ssh -i $HOME\.ssh\vm-smartparking_key.pem azureuser@smartparking-supcom.francecentral.cloudapp.azure.com
```

Résultat attendu : `Are you sure you want to continue connecting?` → `yes`, puis l'invite `azureuser@vm-smartparking:~$`.

Sur la VM. L'utilisateur `azureuser` n'est pas root : il utilise `sudo`.

```bash
# Mise à jour du système, puis redémarrage si demandé
sudo apt update && sudo apt -y full-upgrade
sudo reboot            # se reconnecter après 1 minute

# Mises à jour de sécurité automatiques (déjà actives sur Ubuntu, on vérifie)
systemctl is-enabled unattended-upgrades      # → enabled

# 2 Go de swap : évite que WildFly + MongoDB manquent de mémoire sur 2 Go de RAM
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
free -h                                        # → ligne "Swap:" à 2.0Gi
```

### 3.5 Installer Docker

Commandes officielles de Docker pour Ubuntu :

```bash
sudo apt-get install -y ca-certificates curl git
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker $USER
exit                   # se reconnecter pour que le groupe docker soit pris en compte
```

Vérification après reconnexion :

```bash
docker run --rm hello-world     # → "Hello from Docker!"
docker compose version          # → Docker Compose version v2.x ou plus
```

### 3.6 Récupérer le projet et lancer les services

```bash
sudo mkdir -p /opt/smartparking && sudo chown $USER: /opt/smartparking
git clone https://github.com/amaltrigui19/projet_smart_parking.git /opt/smartparking
cd /opt/smartparking/infra

cp .env.example .env && chmod 600 .env
for i in 1 2 3; do openssl rand -hex 16; done   # 3 mots de passe
nano .env    # DOMAINE=<votre nom DNS> et les 3 mots de passe ; Ctrl+O, Entrée, Ctrl+X
```

Nginx a besoin d'un certificat pour démarrer : on crée d'abord un certificat **temporaire** avec la CA locale, puis on lance les services.

```bash
./scripts/generer-certificats.sh
docker compose up -d --build
docker compose ps        # 4 services "Up", mongo "(healthy)"
```

Vérification (certificat temporaire, d'où `-k`) :

```bash
curl -kI https://$DOMAINE/          # → HTTP/1.1 200 OK
curl -I  http://$DOMAINE/           # → HTTP/1.1 301 Moved Permanently, Location: https://...
```

### 3.7 Certificat Let's Encrypt avec Certbot

```bash
sudo apt install -y certbot
sudo certbot certonly --webroot -w /opt/smartparking/infra/certbot-www \
  -d $DOMAINE --email <adresse-email-equipe> --agree-tos --no-eff-email
```

Résultat attendu : `Successfully received certificate.` et le chemin `/etc/letsencrypt/live/<domaine>/fullchain.pem`.

> Pour s'entraîner sans risquer la limite de Let's Encrypt (5 échecs par heure), ajouter `--staging`, puis relancer sans cette option une fois que tout marche.

Installer le script qui copie le certificat pour Nginx et Mosquitto, et le lancer une première fois :

```bash
sudo cp /opt/smartparking/infra/scripts/certbot-deploy-hook.sh /etc/letsencrypt/renewal-hooks/deploy/smartparking.sh
sudo chmod 755 /etc/letsencrypt/renewal-hooks/deploy/smartparking.sh
sudo RENEWED_LINEAGE=/etc/letsencrypt/live/$DOMAINE /etc/letsencrypt/renewal-hooks/deploy/smartparking.sh
```

Résultat attendu : `Certificat Let's Encrypt installé pour Nginx et Mosquitto.`

**Renouvellement automatique** : le paquet `certbot` installe un minuteur systemd qui tente le renouvellement deux fois par jour. Le certificat (valable 90 jours) est renouvelé 30 jours avant son expiration, puis le script ci-dessus recharge Nginx et Mosquitto.

```bash
systemctl list-timers | grep certbot    # → une ligne certbot.timer
sudo certbot renew --dry-run            # → "Congratulations, all simulated renewals succeeded"
```

### 3.8 Vérification de la VM

Depuis la VM :

```bash
sudo apt install -y mosquitto-clients
cd /opt/smartparking && ./infra/scripts/verifier.sh $DOMAINE    # → "Tout fonctionne."
```

Depuis le PC (PowerShell : taper **`curl.exe`**, car `curl` seul lance `Invoke-WebRequest`) :

```powershell
curl.exe -I https://smartparking-supcom.francecentral.cloudapp.azure.com/
curl.exe https://smartparking-supcom.francecentral.cloudapp.azure.com/api/sante
```

Résultat attendu : `HTTP/1.1 200 OK` **sans** option `-k` (le certificat est reconnu), puis le JSON `{"statut":"ok",...}`.

Certificat vu par un client :

```bash
openssl s_client -connect $DOMAINE:443 -servername $DOMAINE </dev/null 2>/dev/null | openssl x509 -noout -issuer -enddate
# → issuer=C = US, O = Let's Encrypt, CN = ...   notAfter=<dans 90 jours>
```

Test MQTT TLS depuis le Raspberry Pi ou depuis un PC qui a les clients Mosquitto : comme en 2.3, mais avec `-h $DOMAINE` et `--capath /etc/ssl/certs` au lieu de `--cafile …ca.pem`, car Let's Encrypt est reconnu par le système.

**Navigateur** : `https://<domaine>` affiche la PWA avec un cadenas valide : **c'est la démo du sprint 1**.

### 3.9 Différences pour AWS

| Étape | AWS EC2 |
|---|---|
| VM | *Launch instance*, Ubuntu Server 24.04 LTS, **t3.small** (2 Go), disque gp3 de 20 Go, paire de clés `.pem`. Utilisateur `ubuntu` au lieu d'`azureuser` |
| IP fixe | *Elastic IP* → *Allocate* → *Associate* à l'instance |
| Ports | *Security group* : règles entrantes 22 (My IP), 80, 443, 8883 |
| Nom DNS | Let's Encrypt **refuse** les noms `*.amazonaws.com`. Prendre un sous-domaine gratuit, par exemple sur DuckDNS (`smartparking-supcom.duckdns.org` → l'Elastic IP) |
| Suite | identique à partir de 3.4 |

### 3.10 Coût et arrêt de la VM

Ordres de grandeur (à vérifier sur la calculatrice de prix Azure ou AWS, ils varient selon la région) :

| Élément | Quand c'est facturé | Prix approximatif |
|---|---|---|
| VM B1ms (Azure) ou t3.small (AWS) | seulement quand elle **tourne** | ~0,02 USD/h, ~15 USD/mois en continu |
| Disque 30 Go | toujours, même VM arrêtée | ~2 à 3 USD/mois |
| IP publique statique | toujours | ~3,5 USD/mois |

Avec 100 USD de crédit, une VM allumée en permanence tient environ 5 mois. En l'arrêtant hors des séances de travail, le crédit dure toute l'année.

**Arrêter pour de bon (ne plus payer le calcul)** :
- Azure : portail → VM → **Arrêter**. L'état doit devenir **« Arrêté (désalloué) »**. Un `sudo shutdown` depuis la VM donne seulement « Arrêté » : **la VM reste facturée**. En CLI : `az vm deallocate -g rg-smartparking -n vm-smartparking`, puis `az vm start …` pour la relancer.
- AWS : *Instance state* → **Stop instance**.

**Arrêt automatique chaque soir** (Azure) : VM → *Opérations* → *Arrêt automatique* → activé, 23:00, fuseau (UTC+01:00). En CLI : `az vm auto-shutdown -g rg-smartparking -n vm-smartparking --time 2200` (heure UTC).

**Au redémarrage**, rien à refaire : l'IP statique et le nom DNS sont conservés, et les conteneurs repartent seuls (`restart: unless-stopped`). Compter 1 à 2 minutes avant que `https://` réponde.

**Fin du projet** : supprimer le groupe de ressources `rg-smartparking` (VM, disque, IP), sinon disque et IP restent facturés.

**Budget** : Azure → *Gestion des coûts* → *Budgets* → alerte à 50 % et 80 % du crédit.

---

## 4. Dépannage

| Symptôme | Cause | Solution |
|---|---|---|
| `failed to connect to the docker API … dockerDesktopLinuxEngine` | Docker Desktop n'est pas lancé | Lancer Docker Desktop, attendre que la baleine ne bouge plus |
| `curl: (60) schannel: … CERT_TRUST_REVOCATION_STATUS_UNKNOWN` (Windows) | Le curl de Windows cherche une liste de révocation que la CA locale n'a pas | Ajouter `--ssl-no-revoke` (fait par `verifier.sh`) |
| En PowerShell, `curl -I` donne une erreur de paramètre | `curl` = `Invoke-WebRequest` dans PowerShell 5 | Taper `curl.exe` |
| `Error: Problem setting TLS options: File not found` avec `docker compose exec` dans Git Bash | Git Bash transforme `/mosquitto/...` en chemin Windows | Préfixer la commande par `MSYS_NO_PATHCONV=1` |
| `Bind for 0.0.0.0:443 failed: port is already allocated` | Un autre programme utilise 80, 443 ou 8883 | `netstat -ano \| findstr ":443 "` puis arrêter le programme (IIS, autre serveur local) |
| Nginx s'arrête : `cannot load certificate "/etc/nginx/certs/fullchain.pem"` | Certificats absents | `./infra/scripts/generer-certificats.sh` puis `docker compose restart nginx` |
| `502 Bad Gateway` sur `/api/...` | WildFly démarre encore (10 à 30 s) ou a planté | Attendre, puis `docker compose logs wildfly` |
| `/api/sante` répond `503` avec `"mongo":"ko"` ou `"mqtt":"ko"` | Le service indiqué ne tourne pas | `docker compose ps`, puis `docker compose logs mongo` ou `mqtt` |
| `Connection Refused: not authorised` (MQTT) | Mauvais mot de passe, ou `.env` modifié sans recréer le conteneur | Vérifier `.env`, puis `docker compose up -d --force-recreate mqtt` |
| Un message MQTT publié n'arrive jamais | L'ACL interdit ce topic à ce compte (refus silencieux) | Vérifier `infra/mosquitto/acl` et le contrat 5.3 |
| Dans les journaux de `mqtt` : `unexpected eof while reading` venant de l'adresse de WildFly | Normal : `/api/sante` teste seulement que le port 8883 répond, sans faire la négociation TLS | Rien à faire |
| MongoDB refuse le nouveau mot de passe mis dans `.env` | Le compte admin n'est créé qu'au **premier** démarrage, avec un volume vide | Changer le mot de passe dans `mongosh`, ou (en perdant les données) `docker compose down -v` |
| `$'\r': command not found` ou `sh: …demarrer.sh: not found` | Script enregistré avec des fins de ligne Windows (CRLF) | `.gitattributes` force LF ; sinon `sed -i 's/\r$//' <fichier>` |
| Certbot : `Timeout during connect` ou `unauthorized` | Port 80 fermé dans le groupe de sécurité, DNS faux, ou Nginx arrêté | Vérifier 3.3, `Resolve-DnsName`, `docker compose ps` |
| Certbot : `too many failed authorizations` | Limite de Let's Encrypt atteinte | Attendre 1 heure, s'entraîner avec `--staging` |
| `verifier.sh <domaine>` sur la VM : MQTT `Timed out` | La VM n'arrive pas à se joindre par son IP publique | Ajouter `127.0.0.1 <domaine>` dans `/etc/hosts` de la VM |
| La VM rame, WildFly redémarre seul | Mémoire pleine | `docker stats`, `free -h` ; vérifier le swap (3.4) ou passer à B2s |
| SSH : `UNPROTECTED PRIVATE KEY FILE` | Droits trop larges sur la clé `.pem` | Commande `icacls` de 3.4 |
