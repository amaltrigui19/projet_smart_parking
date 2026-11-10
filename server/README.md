# Serveur Smart Parking (infrastructure)

Docker Compose : Nginx (HTTPS, sert le `frontend/` et transmet `/api` au middleware), Mosquitto (MQTT en TLS), MongoDB, WildFly (construit depuis `middleware/`). Le même fichier sert en local et sur la VM AWS.

```
server/
├── docker-compose.yml      # les 4 services
├── .env.example            # modèle des secrets → copier en .env (jamais commité)
├── nginx/nginx.conf
├── mosquitto/              # mosquitto.conf, acl, demarrer.sh
└── scripts/                # generer-certificats.sh, certbot-deploy-hook.sh, verifier.sh
```

Lancer et vérifier : voir [`docs/installation-serveur.md`](../docs/installation-serveur.md).
