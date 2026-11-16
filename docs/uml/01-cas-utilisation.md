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

    subgraph systeme ["Système Smart Parking : conducteur"]
        uc01a(["US-01 S'inscrire"])
        uc01b(["US-01 Se connecter"])
        uc02(["US-02 Voir les places libres en temps réel"])
        uc03(["US-03 Être guidé vers la place libre la plus proche"])
        uc04(["US-04 Réserver et annuler une place"])
        uc05(["US-05 Payer le stationnement, paiement simulé"])
        uc06(["US-06 Consulter mon historique"])
        uc07(["US-07 Recevoir des notifications"])
        detecter(["Détecter l'occupation de chaque place"])
    end

    pi(("Raspberry Pi"))
    push(("Service de push"))

    conducteur --- uc01a
    conducteur --- uc01b
    conducteur --- uc02
    conducteur --- uc03
    conducteur --- uc04
    conducteur --- uc05
    conducteur --- uc06
    conducteur --- uc07

    detecter --- pi
    uc07 --- push

    uc03 -.->|"«include»"| uc02
```

## 2. Interface exploitant

```mermaid
flowchart LR
    exploitant(("Exploitant"))

    subgraph systeme ["Système Smart Parking : exploitant"]
        uc01(["US-01 Se connecter"])
        uc08(["US-08 Suivre le tableau de bord"])
        uc09(["US-09 Recevoir une alerte voiture à cheval"])
        uc10(["US-10 Recevoir une alerte véhicule ventouse"])
        uc11(["US-11 Recevoir une alerte place spéciale sans autorisation"])
        uc12(["US-12 Recevoir une alerte véhicule en zone interdite"])
        uc13(["US-13 Configurer le parking"])
        uc14(["US-14 Gérer utilisateurs et autorisations"])
        uc15(["US-15 Consulter les statistiques"])
        anomalies(["Détecter les anomalies"])
        notifier(["Envoyer une notification push"])
    end

    pi(("Raspberry Pi"))
    push(("Service de push"))

    exploitant --- uc01
    exploitant --- uc08
    exploitant --- uc09
    exploitant --- uc10
    exploitant --- uc11
    exploitant --- uc12
    exploitant --- uc13
    exploitant --- uc14
    exploitant --- uc15

    anomalies --- pi
    notifier --- push

    uc09 -.->|"«include»"| notifier
    uc10 -.->|"«include»"| notifier
    uc11 -.->|"«include»"| notifier
    uc12 -.->|"«include»"| notifier
```

Notation : les seules relations entre cas d'utilisation sont `«include»` (le cas de départ utilise toujours le cas d'arrivée). L'ordre dans le temps (payer après avoir réservé) et les flux de données (le Pi alimente l'état des places) ne sont pas des relations UML : ils sont décrits dans les [diagrammes de séquence](03-sequences.md). Les acteurs principaux sont à gauche, les acteurs secondaires (Raspberry Pi, Service de push) à droite.

## Remarques

- **US-01** est partagée : un exploitant se connecte comme un conducteur, mais ne s'inscrit pas (d'où « S'inscrire » et « Se connecter » séparés côté conducteur). Son compte est créé par un autre exploitant (US-14).
- **Voiture à cheval** (US-09) et **zone interdite** (US-12) sont détectées par le **Pi**, qui les envoie en MQTT. **Ventouse** (US-10) et **place spéciale** (US-11) sont calculées par le **serveur** à partir de l'état des places, de la durée et des autorisations : elles ne passent donc pas par le Pi.
- Le conducteur reçoit lui aussi des notifications (US-07) : réservation confirmée, fin de durée, place à libérer, place prise.
- Hors périmètre, selon le cahier des charges : reconnaissance de plaques ou de visages, verbalisation automatique, guidage routier dans la ville, paiements réels.