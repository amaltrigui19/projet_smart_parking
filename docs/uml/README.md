# Conception UML

Sprint 2 · Vendredi 13 novembre · Brouillon préparé par Eya, **à relire et compléter à trois** (Eya, Amal, Maissa)

Les diagrammes décrivent ce que disent déjà nos documents : les [user stories](../user-stories.md) et les [contrats d'interface](../contrats.md). Ils ne changent aucun contrat. Si un diagramme et un contrat se contredisent, c'est le **contrat** qui fait foi, et il faut corriger le diagramme (ou ouvrir une discussion pour changer le contrat).

| Fichier | Diagramme | Ce qu'il montre | Source principale |
|---|---|---|---|
| [01-cas-utilisation.md](01-cas-utilisation.md) | Cas d'utilisation | Qui fait quoi avec le système (conducteur, exploitant, Raspberry Pi) | user stories US-01 à US-15 |
| [02-classes.md](02-classes.md) | Classes et états | Les objets du métier, leurs liens, et le cycle de vie d'une réservation | contrats 2.14 et 2.8 |
| [03-sequences.md](03-sequences.md) | Séquences | Six scénarios, de bout en bout : connexion, occupation en temps réel, guidage, réservation et paiement, anomalie avec notification, configuration des polygones | contrats 2, 3, 5 |
| [04-deploiement.md](04-deploiement.md) | Déploiement | Où tourne chaque logiciel, sur quel port, avec quel protocole | `server/`, `docs/installation-serveur.md` |

## Comment les voir

- **Sur GitHub** : les diagrammes s'affichent tout seuls dans les fichiers `.md`.
- **Dans VS Code** : installer l'extension *Markdown Preview Mermaid Support*, puis ouvrir l'aperçu du fichier.
- **En ligne** : copier le contenu d'un bloc ` ```mermaid ` dans <https://mermaid.live>, qui permet aussi d'exporter en PNG ou en SVG pour un rapport ou une présentation.

## Conventions

- Mermaid n'a pas de diagramme de cas d'utilisation à proprement parler : on le dessine avec un diagramme de flux (acteurs en cercles, cas d'utilisation en ovales, frontière du système en cadre).
- Les noms des champs, des routes et des types d'anomalie sont ceux des contrats : `cheval`, `ventouse`, `place_speciale`, `zone_interdite`.
- Un seul parking, `P1`, avec 8 places `A1` à `B4`.

## Ce qu'il reste à faire à trois

- Relire chaque diagramme avec la partie qui le concerne : Amal pour l'occupation et la configuration (Pi), Maissa pour les classes et le déploiement, Eya pour les cas d'utilisation et les séquences côté application.
- Ajouter ce qui manque et que vous jugez utile (par exemple le diagramme de séquence de la notification push complète).
- Vérifier que les noms correspondent au code quand il existera (`middleware/iam`, `middleware/core`, `middleware/api`).
