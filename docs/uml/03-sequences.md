# Diagrammes de séquence

Six scénarios de bout en bout. Les noms des routes, des topics MQTT et des messages sont ceux des [contrats](../contrats.md).

Participants :
- **PWA** : l'application dans le navigateur (conducteur ou exploitant) ;
- **Nginx** : la porte d'entrée HTTPS de la VM ;
- **API** : le middleware WildFly (`middleware/api`, qui appelle `iam` et `core`) ;
- **MongoDB** : la base de données ;
- **Mosquitto** : le broker MQTT (TLS, port 8883) ;
- **Pi** : le Raspberry Pi, avec la caméra.

## 1. Inscription et connexion (US-01)

Contrats 2.2 et 2.5. Le jeton est un JWT signé RS256, valable 1 heure.

```mermaid
sequenceDiagram
    autonumber
    actor U as Utilisateur
    participant PWA
    participant Nginx
    participant API as API (iam)
    participant DB as MongoDB

    U->>PWA: saisit email et mot de passe
    PWA->>Nginx: POST /api/auth/connexion (HTTPS)
    Nginx->>API: transmet la requête
    API->>DB: cherche l'utilisateur par email
    DB-->>API: utilisateur avec mot_de_passe_hash

    alt identifiants invalides
        API-->>PWA: 401 identifiants_invalides
        PWA-->>U: email ou mot de passe incorrect
    else compte désactivé
        API-->>PWA: 403 compte_desactive
    else identifiants valides
        API->>API: vérifie le hash, signe le JWT (role, exp 1 h)
        API-->>PWA: 200 jeton et utilisateur
        PWA->>PWA: garde le jeton dans sessionStorage
        alt role = CONDUCTEUR
            PWA-->>U: interface conducteur
        else role = EXPLOITANT
            PWA-->>U: interface exploitant
        end
    end
```

## 2. Occupation d'une place, du Pi à l'écran (US-02, US-08)

Contrat 1.4 (WebSocket) et 5.1 (MQTT). C'est la chaîne complète de la démo du sprint 6.

```mermaid
sequenceDiagram
    autonumber
    participant Cam as Caméra
    participant Pi as Raspberry Pi
    participant MQ as Mosquitto
    participant API as API (core)
    participant DB as MongoDB
    participant PWA as PWA (navigateur)

    Cam->>Pi: image (toutes les 1 à 2 secondes)
    Pi->>Pi: détection YOLO, calcul du recouvrement avec les polygones, lissage sur 3 images
    Note over Pi: Seuls les changements sont publiés, aucune image ne quitte le Pi

    Pi->>MQ: publie parking/P1/places/A3 avec etat=occupee (TLS, QoS 1, retain)
    MQ->>API: livre le message (l'API est abonnée à parking/P1/#)
    API->>DB: met à jour la place A3 (etat, depuis)
    API->>DB: ajoute un document d'historique si le stationnement se termine
    API-->>PWA: WebSocket /api/ws/places : place A3 occupee

    opt une réservation active sur A3
        API->>DB: réservation active passe à en_cours
    end
    opt un conducteur est guidé vers A3
        API->>API: voir le scénario 3, nouveau trajet et notification place_prise
    end

    PWA->>PWA: recolore la place A3 sur le plan SVG
```

## 3. Guidage vers la place libre la plus proche (US-03)

Contrat 2.7. Le plus court chemin est calculé avec Dijkstra dans le graphe de la maquette.

```mermaid
sequenceDiagram
    autonumber
    actor C as Conducteur
    participant PWA
    participant API as API (core)
    participant DB as MongoDB
    participant Push as Service de push

    C->>PWA: appuie sur « Me guider »
    PWA->>API: POST /api/guidage avec le JWT
    API->>API: vérifie le rôle CONDUCTEUR
    API->>DB: lit les places, le graphe et les autorisations du conducteur
    API->>API: écarte les places occupées, réservées, verrouillées par un autre ou sans autorisation
    API->>API: Dijkstra depuis E, égalité : identifiant le plus petit

    alt aucune place possible
        API-->>PWA: 409 parking_complet
    else une place est trouvée
        API->>DB: verrouille la place pour 3 minutes
        API-->>PWA: 200 place, distance_cm, trajet, verrou_jusqu_a
        PWA-->>C: trace le trajet sur le plan
    end

    Note over API,DB: Plus tard, quelqu'un d'autre occupe la place proposée
    API->>API: nouveau calcul de trajet
    API->>DB: verrouille la nouvelle place
    API-)Push: notification place_prise
    Push-)PWA: affiche la notification
    PWA-->>C: nouveau trajet
```

## 4. Réservation et paiement simulé (US-04, US-05)

Contrat 2.8. Le paiement est simulé : aucune donnée bancaire n'est demandée.

```mermaid
sequenceDiagram
    autonumber
    actor C as Conducteur
    participant PWA
    participant API as API (core)
    participant DB as MongoDB
    participant Push as Service de push

    C->>PWA: choisit la place B2 et réserve
    PWA->>API: POST /api/reservations avec place=B2

    alt place pas libre ou verrouillée pour un autre
        API-->>PWA: 409 place_indisponible
    else déjà une réservation active
        API-->>PWA: 409 reservation_deja_active
    else PMR ou électrique sans autorisation
        API-->>PWA: 403 autorisation_requise
    else réservation possible
        API->>DB: crée la réservation (etat active, expire dans 15 minutes)
        API-->>PWA: 201 réservation
        API-)Push: notification reservation_confirmee
    end

    Note over C,DB: La voiture arrive, le Pi signale B2 occupee, la réservation passe à en_cours
    Note over C,DB: La voiture part, la réservation passe à terminee

    C->>PWA: appuie sur « Payer »
    PWA->>API: POST /api/reservations/id/paiement avec moyen=simule
    API->>API: montant = tarif horaire x durée au quart d'heure supérieur
    API->>DB: réservation passe à payee, reçu enregistré
    API-->>PWA: 200 montant, devise TND, reçu
    PWA-->>C: affiche le reçu
```

## 5. Anomalie détectée et notification (US-09 à US-12)

Contrats 5.1 (MQTT) et section 3 (push). Exemple : voiture à cheval sur A3 et A4.

```mermaid
sequenceDiagram
    autonumber
    participant Pi as Raspberry Pi
    participant MQ as Mosquitto
    participant API as API (core)
    participant DB as MongoDB
    participant Push as Service de push
    participant PWA as PWA exploitant
    actor E as Exploitant

    Pi->>Pi: une boîte de véhicule recouvre deux polygones
    Pi->>MQ: publie parking/P1/evenements avec type=cheval, places A3 et A4
    MQ->>API: livre le message
    API->>DB: enregistre l'événement (source=pi, traite=false)
    API-->>PWA: WebSocket : message de type evenement
    API->>DB: lit les abonnements push des exploitants
    API-)Push: notification cheval (urgence haute, durée de vie 1 heure)
    Push-)PWA: livre la notification, même si l'application est fermée
    PWA-->>E: affiche « Voiture à cheval »

    E->>PWA: ouvre l'alerte
    PWA->>API: PATCH /api/evenements/id avec traite=true et un commentaire
    API->>DB: met à jour traite_par et traite_le
    API-->>PWA: 200 événement mis à jour

    Note over API,DB: Ventouse et place_speciale suivent le même parcours,<br/>mais c'est le serveur qui les déclenche (durée dépassée, absence d'autorisation)
```

## 6. Configuration des polygones (US-13)

Contrats 2.10 et 5.3. L'exploitant dessine les places sur une photo prise par le Pi, le serveur les enregistre et les publie au Pi.

```mermaid
sequenceDiagram
    autonumber
    actor E as Exploitant
    participant PWA as PWA (calibration)
    participant API as API (core)
    participant DB as MongoDB
    participant MQ as Mosquitto
    participant Pi as Raspberry Pi

    E->>PWA: charge la photo de référence et dessine les places et les zones
    PWA->>PWA: construit places.json (coordonnées en pixels)
    PWA->>API: PUT /api/config/polygones avec le fichier

    alt fichier invalide
        API-->>PWA: 400 config_invalide (identifiant en double, polygone hors image, type inconnu)
        PWA-->>E: affiche l'erreur
    else fichier valide
        API->>DB: enregistre les polygones
        API->>MQ: publie parking/P1/config avec le fichier complet (retain, QoS 1)
        API-->>PWA: 200 fichier enregistré
        MQ->>Pi: livre la configuration (le Pi est abonné à parking/P1/config)
        Pi->>Pi: recharge les polygones sans redémarrer
    end
```
