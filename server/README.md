# Serveur Smart Parking (infrastructure)

Docker Compose : Nginx (HTTPS, sert le front office sur `/` et le back office sur `/admin/`, transmet `/api` et `/.well-known/jwks.json` au middleware), Mosquitto (MQTT en TLS), MongoDB, WildFly (construit depuis `middleware/`). Le même fichier sert en local et sur la VM AWS.

```
server/
├── docker-compose.yml      # les 4 services
├── .env.example            # modèle des secrets → copier en .env (jamais commité)
├── nginx/nginx.conf
├── mongo/                  # seed.js, index.js, examples.js, check-schema.js, read-back.js
├── mosquitto/              # mosquitto.conf, acl, start.sh
└── scripts/                # generate-certificates.sh, generate-jwt-keys.sh, check-server.sh, test-auth.sh,
                            # test-polygons.sh, run-mongo.sh, certbot-deploy-hook.sh, deploy.sh
```

Lancer et vérifier : voir [`docs/installation-serveur.md`](../docs/installation-serveur.md).

## CI/CD (GitHub Actions)

Deux workflows dans `.github/workflows/` :

| Workflow | Quand | Ce qu'il fait |
|---|---|---|
| `ci.yml` (**CI**) | chaque pull request, chaque push sur `main` | `mvn clean verify` (compilation + tests JUnit de `iam`, `core`, `api`), tests JS et Python du frontend, construction de l'image WildFly, vérification de `docker-compose.yml` |
| `deploy.yml` (**Deploy**) | après une CI verte sur `main` (ou à la main : Actions → Deploy → *Run workflow*) | construit l'image WildFly, l'envoie sur GHCR avec le **commit** comme tag, puis la déploie sur la VM par SSH |

Déroulé d'un déploiement :

1. L'image `ghcr.io/amaltrigui19/projet_smart_parking/wildfly:<commit>` est construite (le `Dockerfile` relance les tests) et envoyée sur GHCR.
2. Par SSH, la VM met à jour `/opt/smartparking` sur ce commit (`git merge --ff-only` : si quelqu'un a modifié un fichier suivi sur la VM, le déploiement s'arrête au lieu d'écraser).
3. `scripts/deploy.sh` écrit `WILDFLY_IMAGE=<image>` dans `.env`, fait `docker compose pull wildfly` puis `up -d --no-build`, recharge Nginx (sa configuration et le `frontend/` viennent du dépôt), attend `/api/health` puis supprime les images inutilisées.
4. Le workflow vérifie `https://smartparking-supcom.duckdns.org/api/health` depuis Internet.

Versions fixées partout : images Docker avec un numéro exact (pas de `latest`), image WildFly identifiée par son commit.

### Mise en place (une seule fois)

**Sur la VM** : créer une clé SSH réservée au déploiement (pas la clé AWS personnelle).

```bash
ssh-keygen -t ed25519 -N '' -C github-deploy -f ~/github-deploy
cat ~/github-deploy.pub >> ~/.ssh/authorized_keys
cat ~/github-deploy          # contenu à copier dans le secret VM_SSH_KEY, puis :
rm ~/github-deploy ~/github-deploy.pub
```

**Sur le PC** : l'empreinte du serveur, pour le secret `VM_SSH_KNOWN_HOSTS` :

```bash
ssh-keyscan -t ed25519 smartparking-supcom.duckdns.org
```

**Sur GitHub** (propriétaire du dépôt) : *Settings → Secrets and variables → Actions → New repository secret*.

| Secret | Valeur |
|---|---|
| `VM_HOST` | `smartparking-supcom.duckdns.org` |
| `VM_USER` | `ubuntu` |
| `VM_SSH_KEY` | la clé privée `github-deploy` (tout le fichier, lignes `BEGIN` et `END` comprises) |
| `VM_SSH_KNOWN_HOSTS` | la ligne donnée par `ssh-keyscan` |

Puis *Settings → Branches → Add rule* sur `main` : **Require status checks to pass**, cocher les trois jobs de la CI (ils n'apparaissent qu'après une première exécution).

À vérifier :
- le port **22** du groupe de sécurité AWS doit accepter `0.0.0.0/0` : les machines de GitHub n'ont pas d'adresse fixe. L'accès reste protégé par la clé.
- aucun secret n'est à ajouter pour GHCR : le workflow utilise le jeton temporaire `GITHUB_TOKEN`, et la VM se déconnecte de GHCR à la fin du déploiement.

### Revenir à une version précédente

Sur la VM, relancer le script avec le tag d'un ancien commit (liste sur la page du paquet `wildfly`, onglet *Packages* du dépôt) :

```bash
cd /opt/smartparking
echo "<jeton GitHub avec read:packages>" | docker login ghcr.io -u <utilisateur> --password-stdin
bash server/scripts/deploy.sh ghcr.io/amaltrigui19/projet_smart_parking/wildfly:<ancien-commit>
docker logout ghcr.io
```

Si le paquet GHCR est rendu public (page du paquet → *Package settings*), le `docker login` n'est plus nécessaire.

### Limites connues

- Une modification de `mosquitto/` demande un redémarrage à la main : `docker compose restart mqtt`.
- Le fichier local `docker-compose.override.yml` de la VM (MongoDB 7.0, voir `docs/installation-serveur.md` §4.4) reste en place et n'est pas touché.
