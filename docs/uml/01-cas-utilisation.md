# Diagrammes de cas d'utilisation

Source : [user stories](../user-stories.md) (US-01 à US-15) et cahier des charges. Chaque cas d'utilisation porte le numéro de sa story. Deux diagrammes, un par interface, pour rester lisibles.

## Les acteurs

| Acteur | Rôle |
|---|---|
| **Conducteur** | Utilise l'interface conducteur de la PWA (rôle `CONDUCTEUR`). |
| **Exploitant** | Gère le parking depuis l'interface exploitant de la PWA (rôle `EXPLOITANT`). |
| **Raspberry Pi** | Acteur technique : il analyse l'image de la caméra, envoie l'état des places et les anomalies. |
| **Service de push** | Acteur externe (Google, Mozilla, Apple) qui livre les notifications au navigateur. |

## 1. Interface conducteur

```mermaid
flowchart LR
    conducteur(("Conducteur"))
    pi(("Raspberry Pi"))
    push(("Service de push"))

    subgraph systeme ["Système Smart Parking : conducteur"]
        uc01(["US-01 S'inscrire et se connecter"])
        uc02(["US-02 Voir les places libres en temps réel"])
        uc03(["US-03 Être guidé vers la place libre la plus proche"])
        uc04(["US-04 Réserver et annuler une place"])
        uc05(["US-05 Payer le stationnement, paiement simulé"])
        uc06(["US-06 Consulter mon historique"])
        uc07(["US-07 Recevoir des notifications"])
        detecter(["Détecter l'occupation de chaque place"])
    end

    conducteur --- uc01
    conducteur --- uc02
    conducteur --- uc03
    conducteur --- uc04
    conducteur --- uc05
    conducteur --- uc06
    conducteur --- uc07

    pi --- detecter
    push --- uc07

    uc03 -.->|"inclut"| uc02
    uc05 -.->|"suit"| uc04
    uc02 -.->|"alimenté par"| detecter
```

## 2. Interface exploitant

```mermaid
flowchart LR
    exploitant(("Exploitant"))
    pi(("Raspberry Pi"))
    push(("Service de push"))

    subgraph systeme ["Système Smart Parking : exploitant"]
        uc01(["US-01 Se connecter"])
        uc08(["US-08 Suivre le tableau de bord"])
        uc09(["US-09 Alerte voiture à cheval"])
        uc10(["US-10 Alerte véhicule ventouse"])
        uc11(["US-11 Alerte place spéciale sans autorisation"])
        uc12(["US-12 Alerte véhicule en zone interdite"])
        uc13(["US-13 Configurer le parking"])
        uc14(["US-14 Gérer utilisateurs et autorisations"])
        uc15(["US-15 Consulter les statistiques"])
        anomalies(["Détecter les anomalies"])
        notifier(["Envoyer une notification push"])
    end

    exploitant --- uc01
    exploitant --- uc08
    exploitant --- uc09
    exploitant --- uc10
    exploitant --- uc11
    exploitant --- uc12
    exploitant --- uc13
    exploitant --- uc14
    exploitant --- uc15

    pi --- anomalies
    push --- notifier

    uc09 -.->|"déclenché par"| anomalies
    uc12 -.->|"déclenché par"| anomalies
    uc09 -.->|"utilise"| notifier
    uc10 -.->|"utilise"| notifier
    uc11 -.->|"utilise"| notifier
    uc12 -.->|"utilise"| notifier
```

## Remarques

- **US-01** est partagée : un exploitant se connecte comme un conducteur, mais ne s'inscrit pas. Son compte est créé par un autre exploitant (US-14).
- **Voiture à cheval** (US-09) et **zone interdite** (US-12) sont détectées par le **Pi**, qui les envoie en MQTT. **Ventouse** (US-10) et **place spéciale** (US-11) sont calculées par le **serveur** à partir de l'état des places, de la durée et des autorisations : elles n'ont donc pas de lien direct avec le Pi dans le diagramme.
- Le conducteur reçoit lui aussi des notifications (US-07) : réservation confirmée, fin de durée, place à libérer, place prise.
- Hors périmètre, selon le cahier des charges : reconnaissance de plaques ou de visages, verbalisation automatique, guidage routier dans la ville, paiements réels.
