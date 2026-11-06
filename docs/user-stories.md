# User stories

Projet Smart Parking · Sprint 1 (2–6 nov.) · Rédaction commune : Eya, Amal, Maissa

Format : **En tant que** <rôle>, **je veux** <action> **afin de** <bénéfice>.
Chaque story a des **critères d'acceptation** (ce qu'on vérifie pour dire « c'est fini »), une **priorité** et le **sprint visé** (voir le planning du [README](../README.md#planning-10-sprints)).

Deux rôles : **conducteur** (`CONDUCTEUR`) et **exploitant** (`EXPLOITANT`). Les noms d'anomalies sont ceux des contrats : `cheval`, `ventouse`, `place_speciale`, `zone_interdite`.

## Vue d'ensemble

| ID | Story | Rôle | Priorité | Sprint |
|---|---|---|---|---|
| [US-01](#us-01--inscription-et-connexion) | Inscription et connexion | les deux | haute | 3 |
| [US-02](#us-02--voir-les-places-libres-en-temps-réel) | Voir les places libres en temps réel | conducteur | haute | 4 |
| [US-03](#us-03--être-guidé-vers-la-place-libre-la-plus-proche) | Être guidé vers la place libre la plus proche | conducteur | haute | 7–8 |
| [US-04](#us-04--réserver-une-place-et-annuler) | Réserver une place et annuler | conducteur | moyenne | 6 |
| [US-05](#us-05--payer-le-stationnement-paiement-simulé) | Payer le stationnement (simulé) | conducteur | moyenne | 7 |
| [US-06](#us-06--consulter-mon-historique) | Consulter mon historique | conducteur | basse | 7 |
| [US-07](#us-07--recevoir-des-notifications-conducteur) | Recevoir des notifications | conducteur | moyenne | 9 |
| [US-08](#us-08--suivre-le-parking-sur-un-tableau-de-bord) | Tableau de bord en temps réel | exploitant | haute | 5 |
| [US-09](#us-09--être-alerté-dune-voiture-à-cheval) | Alerte voiture à cheval | exploitant | haute | 8 |
| [US-10](#us-10--être-alerté-dun-véhicule-ventouse) | Alerte véhicule ventouse | exploitant | haute | 8 |
| [US-11](#us-11--être-alerté-dune-place-spéciale-occupée-sans-autorisation) | Alerte place spéciale sans autorisation | exploitant | moyenne | 8 |
| [US-12](#us-12--être-alerté-dun-véhicule-en-zone-interdite) | Alerte zone interdite | exploitant | moyenne | 8 |
| [US-13](#us-13--configurer-le-parking) | Configurer le parking | exploitant | haute | 2–6 |
| [US-14](#us-14--gérer-les-utilisateurs-et-les-autorisations) | Gérer les utilisateurs et les autorisations | exploitant | moyenne | 6 |
| [US-15](#us-15--consulter-les-statistiques) | Consulter les statistiques | exploitant | basse | 9 |

**Priorité** : *haute* = sans elle, pas de démo ; *moyenne* = attendue dans le produit final ; *basse* = faite si le temps le permet.

---

## Conducteur

### US-01 · Inscription et connexion

**En tant que** conducteur ou exploitant, **je veux** créer un compte et me connecter **afin d'** accéder à l'interface qui correspond à mon rôle.

Priorité : **haute** · Sprint : 3

Critères d'acceptation :
- Je m'inscris avec nom, email et mot de passe (8 caractères minimum) ; le compte créé a le rôle `CONDUCTEUR`.
- Un email déjà utilisé est refusé avec un message clair.
- À la connexion, je reçois un jeton (JWT) qui contient mon rôle et expire au bout d'un temps limité.
- Un conducteur arrive sur l'interface conducteur, un exploitant sur l'interface exploitant.
- Un mauvais mot de passe affiche « email ou mot de passe incorrect » (sans dire lequel des deux est faux).
- Les mots de passe sont stockés hachés, jamais en clair.
- Les comptes exploitant ne se créent pas par inscription publique : ils sont créés par un exploitant (voir US-14).

### US-02 · Voir les places libres en temps réel

**En tant que** conducteur, **je veux** voir sur un plan quelles places sont libres **afin de** savoir s'il reste de la place avant d'arriver.

Priorité : **haute** · Sprint : 4

Critères d'acceptation :
- Le plan affiche les 8 places avec leur état : libre, occupée ou réservée, chacun d'une couleur différente.
- Les places PMR et électriques ont un pictogramme.
- Le nombre de places libres est affiché en haut de l'écran.
- Quand une voiture se gare ou part sur la maquette, le plan se met à jour **sans recharger la page**, en moins de 10 secondes.
- Si la connexion temps réel est coupée, un bandeau l'indique et la page se reconnecte toute seule.

### US-03 · Être guidé vers la place libre la plus proche

**En tant que** conducteur, **je veux** être guidé vers la place libre la plus proche de l'entrée **afin de** me garer vite sans tourner dans le parking.

Priorité : **haute** · Sprint : 7–8

Critères d'acceptation :
- Le serveur choisit la place libre la plus proche par le plus court chemin dans le graphe du parking (Dijkstra ou A*).
- Le trajet (entrée → allée → place) est dessiné sur le plan.
- Les places PMR et électriques ne sont proposées qu'aux conducteurs qui ont l'autorisation correspondante.
- La place proposée est **verrouillée 3 minutes** : elle n'est proposée à aucun autre conducteur pendant ce temps.
- Si la place est prise entre-temps, un nouveau trajet est calculé et je suis prévenu.
- S'il n'y a aucune place libre, l'application affiche « parking complet ».

### US-04 · Réserver une place et annuler

**En tant que** conducteur, **je veux** réserver une place à l'avance, et pouvoir annuler, **afin d'** être sûr d'avoir une place en arrivant sans bloquer les autres si je ne viens plus.

Priorité : **moyenne** · Sprint : 6

Critères d'acceptation :
- Je ne peux réserver qu'une place libre, et une seule réservation active à la fois.
- La place réservée apparaît « réservée » sur le plan de tous les utilisateurs.
- Si je n'arrive pas dans les 15 minutes, la réservation est annulée automatiquement et la place redevient libre.
- Je ne peux réserver une place PMR ou électrique que si j'ai l'autorisation correspondante.
- Une confirmation s'affiche (et une notification si je les ai activées, voir US-07).
- Je peux annuler ma réservation tant que je ne suis pas garé ; la place redevient libre immédiatement.
- Je ne peux pas annuler la réservation d'un autre conducteur.

### US-05 · Payer le stationnement (paiement simulé)

**En tant que** conducteur, **je veux** payer mon stationnement depuis l'application **afin de** ne pas passer par une borne.

Priorité : **moyenne** · Sprint : 7

Critères d'acceptation :
- Le montant est calculé à partir de la durée et du tarif horaire fixé par l'exploitant.
- Le paiement est **simulé** : aucune vraie carte, aucun service bancaire ; un bouton « Payer » suffit.
- Après paiement, la réservation passe à l'état « payée » et un reçu s'affiche (montant, date, place).
- Un même stationnement ne peut pas être payé deux fois.

### US-06 · Consulter mon historique

**En tant que** conducteur, **je veux** voir mes réservations et paiements passés **afin de** suivre mes dépenses.

Priorité : **basse** · Sprint : 7

Critères d'acceptation :
- La liste montre, du plus récent au plus ancien : date, place, durée, montant, état (annulée, payée, expirée).
- Je ne vois que mon propre historique.

### US-07 · Recevoir des notifications (conducteur)

**En tant que** conducteur, **je veux** recevoir des notifications sur mon téléphone **afin d'** être prévenu même quand l'application est fermée.

Priorité : **moyenne** · Sprint : 9

Critères d'acceptation :
- L'application demande la permission seulement quand je clique sur « Activer les notifications ».
- Je reçois : réservation confirmée, fin de durée proche (10 minutes avant), durée dépassée (place à libérer), place proposée prise par un autre.
- Un clic sur la notification ouvre la bonne page de l'application.
- Je peux désactiver les notifications.

---

## Exploitant

### US-08 · Suivre le parking sur un tableau de bord

**En tant qu'** exploitant, **je veux** un tableau de bord en temps réel **afin de** connaître l'état du parking d'un coup d'œil.

Priorité : **haute** · Sprint : 5

Critères d'acceptation :
- Le tableau de bord affiche : places libres, occupées et réservées (nombre et plan), taux d'occupation, alertes non traitées.
- Il affiche l'état du Raspberry Pi : en ligne ou hors ligne, images par seconde, température.
- Si le Pi n'a rien envoyé depuis plus de 3 minutes, il est affiché « hors ligne » en rouge.
- Les chiffres se mettent à jour sans recharger la page.

### US-09 · Être alerté d'une voiture à cheval

**En tant qu'** exploitant, **je veux** être alerté quand une voiture occupe deux places **afin de** faire libérer la place gâchée.

Priorité : **haute** · Sprint : 8

Critères d'acceptation :
- Quand une voiture de la maquette est posée sur deux places, une alerte `cheval` apparaît avec le numéro des deux places.
- Une seule alerte est créée par anomalie (pas une par image analysée).
- Je reçois une notification push si je les ai activées.
- Je peux marquer l'alerte comme traitée ; elle reste visible dans l'historique des alertes.

### US-10 · Être alerté d'un véhicule ventouse

**En tant qu'** exploitant, **je veux** être alerté quand un véhicule reste plus longtemps que la durée autorisée **afin de** libérer les places bloquées.

Priorité : **haute** · Sprint : 8

Critères d'acceptation :
- Chaque place a une durée maximale (en minutes) réglable par l'exploitant.
- Le serveur calcule le dépassement à partir de l'heure d'arrivée envoyée par le Pi ; une alerte `ventouse` est créée dès que la durée est dépassée.
- L'alerte indique la place et la durée de stationnement.
- Le conducteur concerné (s'il a réservé la place) reçoit « place à libérer », pas l'alerte `ventouse`.

### US-11 · Être alerté d'une place spéciale occupée sans autorisation

**En tant qu'** exploitant, **je veux** être alerté quand une place PMR ou électrique est occupée sans autorisation **afin de** faire respecter ces places.

Priorité : **moyenne** · Sprint : 8

Critères d'acceptation :
- Une place PMR ou électrique occupée sans réservation d'un conducteur autorisé déclenche une alerte `place_speciale`.
- L'alerte indique la place et son type.
- Limite connue : la caméra ne lit ni badge ni plaque (vie privée) ; la vérification repose sur les réservations et les autorisations enregistrées.

### US-12 · Être alerté d'un véhicule en zone interdite

**En tant qu'** exploitant, **je veux** être alerté quand un véhicule stationne dans une allée ou devant la sortie **afin d'** éviter que la circulation soit bloquée.

Priorité : **moyenne** · Sprint : 8

Critères d'acceptation :
- Une voiture posée dans la zone interdite de la maquette déclenche une alerte `zone_interdite` avec le nom de la zone.
- Une seule alerte par présence ; une nouvelle alerte si le véhicule part puis revient.
- Je peux marquer l'alerte comme traitée.

### US-13 · Configurer le parking

**En tant qu'** exploitant, **je veux** configurer le plan, les places et les zones **afin d'** adapter le système à mon parking sans modifier le code.

Priorité : **haute** · Sprint : 2–6

Critères d'acceptation :
- Je dessine le contour de chaque place et de chaque zone interdite sur une photo de la caméra (outil de calibration).
- Pour chaque place, je choisis le type (normale, PMR, électrique) et la durée maximale.
- Je règle le tarif horaire.
- La configuration est enregistrée sur le serveur et le Raspberry Pi la reçoit sans redémarrage manuel.
- Une configuration invalide (place sans contour, identifiant en double) est refusée avec un message clair.

### US-14 · Gérer les utilisateurs et les autorisations

**En tant qu'** exploitant, **je veux** gérer les comptes et les autorisations PMR et électriques **afin de** contrôler qui peut utiliser les places spéciales.

Priorité : **moyenne** · Sprint : 6

Critères d'acceptation :
- Je vois la liste des utilisateurs avec leur rôle et leurs autorisations.
- Je peux créer un compte exploitant, désactiver un compte, donner ou retirer une autorisation PMR ou électrique (avec une date de fin facultative).
- Un compte désactivé ne peut plus se connecter.
- Un conducteur n'a jamais accès à ces écrans ni à ces données (le serveur répond « accès refusé »).

### US-15 · Consulter les statistiques

**En tant qu'** exploitant, **je veux** des statistiques d'utilisation **afin de** mieux dimensionner et tarifer le parking.

Priorité : **basse** · Sprint : 9

Critères d'acceptation :
- Je vois sur une période choisie (jour, semaine) : taux d'occupation, heures de pointe, durée moyenne de stationnement, nombre d'infractions par type.
- Les chiffres sont présentés en graphiques simples.
- Les statistiques ne contiennent aucune donnée personnelle des conducteurs.

---

## Exigences communes à toutes les stories

- **Vie privée (privacy by design)** : les images restent sur le Raspberry Pi, sont analysées en mémoire puis effacées ; aucune image, plaque ou visage n'est envoyé ni enregistré.
- **Sécurité** : tout passe en HTTPS (et MQTT en TLS) ; le serveur vérifie le rôle à chaque appel d'API.
- **Hors périmètre** : reconnaissance de plaques, verbalisation automatique, guidage routier en ville, vrais paiements.

## Valeurs à confirmer ensemble

Ces valeurs sont proposées ici ; elles seront reprises dans `docs/contrats.md` une fois validées.

| Valeur | Proposition |
|---|---|
| Durée du verrouillage de la place proposée (US-03) | 3 minutes |
| Délai avant annulation d'une réservation si le conducteur n'arrive pas (US-04) | 15 minutes |
| Préavis de la notification « fin de durée » (US-07) | 10 minutes |
| Pi considéré hors ligne après (US-08) | 3 minutes sans statut |
| Délai d'affichage d'un changement de place (US-02) | moins de 10 secondes |
