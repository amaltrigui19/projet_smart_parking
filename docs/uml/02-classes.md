# Diagramme de classes et états

Source : [contrats, section 2.14](../contrats.md) (collections MongoDB), 2.7 (guidage), 2.8 (réservations), 2.9 (événements) et 4.3 (plan). Chaque classe correspond à une collection MongoDB, ou à un objet **imbriqué** dans un document (indiqué en note).

Aucune classe ne contient d'image, de plaque ou de visage : uniquement des états, des horaires et des comptes (*privacy by design*).

## Modèle du métier

```mermaid
classDiagram
    direction LR

    class Parking {
        +String id
        +String nom
        +int[] resolution
        +int[] dimensions_cm
        +Parametres parametres
        +PiStatut pi
        +Date maj_le
    }

    class Place {
        +String id
        +TypePlace type
        +int duree_max_min
        +int[][] poly
        +int[] rect_cm
        +String noeud
        +EtatPlace etat
        +Date depuis
    }

    class ZoneInterdite {
        +String id
        +int[][] poly
        +int[] rect_cm
    }

    class Noeud {
        +String id
        +TypeNoeud type
        +float x
        +float y
    }

    class Arete {
        +String de
        +String a
        +float longueur_cm
    }

    class Verrou {
        +String utilisateur
        +Date jusqu_a
    }

    class Utilisateur {
        +String id
        +String nom
        +String email
        +String mot_de_passe_hash
        +Role role
        +boolean actif
        +Date cree_le
    }

    class Autorisation {
        +String id
        +TypeAutorisation type
        +Date debut
        +Date fin
        +String accordee_par
    }

    class Reservation {
        +String id
        +EtatReservation etat
        +Date creee_le
        +Date expire_le
        +Date debut
        +Date fin
        +float montant
        +String devise
    }

    class Paiement {
        +String moyen
        +Date paye_le
        +String recu
    }

    class Evenement {
        +String id
        +TypeEvenement type
        +String[] places
        +String zone
        +Date debut
        +Date fin
        +Source source
        +boolean traite
        +String traite_par
        +Date traite_le
        +String commentaire
    }

    class AbonnementPush {
        +String id
        +String endpoint
        +String p256dh
        +String auth
        +Date cree_le
    }

    class HistoriqueOccupation {
        +String id
        +Date debut
        +Date fin
        +int duree_min
    }

    class Parametres {
        +float tarif_horaire
        +int verrou_min
        +int pi_hors_ligne_min
        +int preavis_fin_duree_min
    }

    class PiStatut {
        +boolean en_ligne
        +float fps
        +float temp_cpu
        +Date recu_le
    }

    class TypePlace {
        <<enumeration>>
        normale
        pmr
        electrique
    }

    class EtatPlace {
        <<enumeration>>
        libre
        occupee
        reservee
    }

    class Role {
        <<enumeration>>
        CONDUCTEUR
        EXPLOITANT
    }

    class TypeAutorisation {
        <<enumeration>>
        pmr
        electrique
    }

    class TypeEvenement {
        <<enumeration>>
        cheval
        zone_interdite
        ventouse
        place_speciale
    }

    class Source {
        <<enumeration>>
        pi
        serveur
    }

    Parking "1" *-- "8" Place : contient
    Parking "1" *-- "0..*" ZoneInterdite : définit
    Parking "1" *-- "*" Noeud : graphe
    Parking "1" *-- "*" Arete : graphe
    Parking "1" *-- "1" Parametres
    Parking "1" *-- "1" PiStatut
    Arete "*" --> "2" Noeud : relie
    Place "1" --> "1" Noeud : repérée par
    Place "1" *-- "0..1" Verrou
    Verrou "0..1" --> "1" Utilisateur : pour
    Utilisateur "1" --> "0..*" Autorisation : possède
    Utilisateur "1" --> "0..*" Reservation : effectue
    Place "1" <-- "0..*" Reservation : porte sur
    Reservation "1" *-- "0..1" Paiement
    Evenement "*" --> "0..*" Place : concerne
    Evenement "*" --> "0..1" ZoneInterdite : concerne
    Utilisateur "1" --> "0..*" AbonnementPush : reçoit sur
    Place "1" <-- "0..*" HistoriqueOccupation : historise

    Place ..> TypePlace
    Place ..> EtatPlace
    Utilisateur ..> Role
    Autorisation ..> TypeAutorisation
    Evenement ..> TypeEvenement
    Evenement ..> Source
```

Notes de lecture :
- **Imbriqués dans le document `parkings`** : le graphe (`Noeud`, `Arete`), les `ZoneInterdite`, les `Parametres` et le `PiStatut`.
- **Imbriqués dans `places`** : le `Verrou` (`null` s'il n'y a pas de guidage en cours).
- **Imbriqué dans `reservations`** : le `Paiement`.
- L'état `reservee` d'une place n'est **pas** envoyé par le Pi : le serveur le calcule quand une place libre a une réservation active. Le Pi ne connaît que `libre` et `occupee`.
- `HistoriqueOccupation` contient un document par stationnement terminé, pour les statistiques.

## Cycle de vie d'une réservation

Source : contrats, section 2.8.

```mermaid
stateDiagram-v2
    [*] --> active : POST /api/reservations
    active --> en_cours : la voiture arrive, la place devient occupee
    active --> annulee : DELETE /api/reservations/id
    active --> expiree : 15 minutes sans arrivée
    en_cours --> terminee : la voiture part
    en_cours --> payee : paiement avant le départ
    terminee --> payee : paiement
    annulee --> [*]
    expiree --> [*]
    payee --> [*]
```

Règles associées :
- Un conducteur n'a qu'**une** réservation `active` ou `en_cours` à la fois.
- Le paiement est **simulé** : le moyen vaut toujours `simule`, aucune donnée bancaire n'est demandée ni stockée.
- Le montant est le tarif horaire multiplié par la durée arrondie au quart d'heure supérieur (un quart d'heure au minimum).

## Types de place et autorisations

| Type de place | Qui peut la recevoir par le guidage |
|---|---|
| `normale` | tous les conducteurs |
| `pmr` | un conducteur ayant une autorisation `pmr` valide |
| `electrique` | un conducteur ayant une autorisation `electrique` valide |

La caméra ne voit pas les badges : l'alerte `place_speciale` s'appuie sur les autorisations enregistrées dans le système.
