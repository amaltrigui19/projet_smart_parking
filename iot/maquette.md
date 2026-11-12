# Maquette du parking : matériel et protocole des photos de référence

Sprint 2 (lundi–mardi, tâche commune) · Rédaction : Maissa · D'après `docs/contrats.md` §4 (plan de la maquette) et le cahier des charges §4 (équipement)

> **État au 13 novembre 2026 : la maquette n'est pas construite**, faute de matériel. **Si le matériel n'arrive pas cette semaine, la construction et les photos de référence passent au sprint 3** (lundi–mardi, en commun), avant l'annotation des lots 2 et 3. En attendant, le lot 1 a été fait sur des datasets publics (PR #9).

La maquette sert à trois choses :
1. **les photos d'entraînement** de YOLO (lots 2 et 3) ;
2. **la calibration** des polygones des places (outil `frontend/calibration.html`) ;
3. **la démo** finale.

Tout ce qui suit découle du plan validé (contrat §4) : 60 × 40 cm, 8 places, une allée centrale et une zone interdite `allee1`.

---

## 1. Liste du matériel

| # | Élément | Quantité | Caractéristiques | Pourquoi |
|---|---|---|---|---|
| 1 | Plaque de fond | 1 | Carton épais, mousse EVA ou contreplaqué, **au moins 66 × 46 cm** (60 × 40 cm de maquette et 3 cm de marge de chaque côté), **gris moyen mat** (peinture ou papier gris non brillant) | Contraste avec les voitures, pas de reflets |
| 2 | Ruban adhésif **blanc mat** | 1 rouleau | Largeur 1 cm (type ruban de masquage blanc ou ruban de marquage) | Lignes des places, nettes sur l'image |
| 3 | Ruban de couleur (jaune ou rouge) | 1 rouleau | Pour hachurer la zone interdite `allee1` | Zone interdite visible |
| 4 | Pictogrammes | 2 | « PMR » (A1) et « borne électrique » (B4), imprimés en noir sur blanc, environ 4 × 4 cm | Repérer les places spéciales |
| 5 | Voitures miniatures | **8 à 10** | Échelle **1/43** (≈ 10 × 4,5 cm) ou **1/64** (≈ 7,5 × 3,5 cm), qui tiennent dans une place de 8 × 13 cm. **Couleurs vives et variées** (rouge, bleu, jaune, blanc, vert, orange). **Pas de voiture noire ni gris foncé** | Le test S1 a montré des oublis sur les voitures sombres. 8 voitures pour remplir le parking, plus 1 ou 2 pour les cas « à cheval » et « zone interdite » |
| 6 | Caméra | 1 | Module caméra du Raspberry Pi (v2 ou v3) ou webcam USB | Prise de vue |
| 7 | Support de caméra | 1 | **Rigide**, objectif **à la verticale** du centre de la maquette, hauteur réglable entre **55 et 70 cm** (potence, bras articulé, ou planche fixée sur deux montants) | Une caméra qui bouge oblige à redessiner les polygones |
| 8 | Éclairage | 2 | Lampes ou bandeaux **LED à lumière constante** (sans scintillement), placés **de chaque côté**, si possible diffusés (papier calque) | Pas d'ombre portée ni de reflet ; même image le matin et le soir |
| 9 | Raspberry Pi 4 | 1 | 4 Go, carte microSD de 32 Go, alimentation USB-C 5 V / 3 A, dissipateur ou ventilateur | Prise de vue, puis inférence |
| 10 | Petit matériel | — | Règle de 60 cm, équerre, crayon, cutter, ruban adhésif double face, serre-câbles | Construction |

Hauteur de la caméra, pour voir toute la maquette avec sa marge (66 × 46 cm) :
- module caméra **v2** : **≈ 55 cm** au minimum (angle de vue 62° × 49°) ;
- module caméra **3** : **≈ 62 cm** au minimum (angle de vue 66° × 41°) ;
- webcam : à régler à l'œil avec la photo de test (étape 3.2).

On vise 60 à 70 cm. Plus haut, les voitures deviennent trop petites sur l'image.

**Variante si le Raspberry Pi manque** (cahier des charges §4) : la même webcam branchée sur un ordinateur portable, avec le même `capture.py` (`--source opencv`). L'architecture ne change pas.

---

## 2. Construction (plan du contrat §4.1)

Repère : origine en haut à gauche, `x` vers la droite, `y` vers le bas, en centimètres.

1. Tracer au crayon le cadre de **60 × 40 cm**, centré sur la plaque (3 cm de marge).
2. **Allée centrale** : de `y = 13` à `y = 27` (14 cm de large), sur toute la largeur.
3. **Rangée A** (en haut, de `y = 0` à `13`) et **rangée B** (en bas, de `y = 27` à `40`) : 4 places de **8 cm** de large chacune, aux `x` 14–22, 22–30, 30–38 et 38–46.
4. Poser le **ruban blanc** sur les bords des places. Le ruban se pose **à l'extérieur** du trait, pour que chaque place garde 8 × 13 cm utiles.
5. Pictogrammes : **PMR sur A1**, **électrique sur B4**.
6. **Zone interdite `allee1`** : `x` de 50 à 60, `y` de 13 à 27, hachurée au ruban de couleur. Entrée à gauche, sortie à droite.
7. Écrire les numéros `A1`…`A4` et `B1`…`B4` **sur le bord extérieur**, hors des places, pour qu'ils ne gênent pas la détection.

Vérification : mesurer chaque place (8 × 13 cm, ± 0,5 cm) et prendre une photo de la maquette vide **avant** de fixer la caméra.

---

## 3. Installation et réglage de la caméra

1. Fixer le support. L'objectif doit être **parallèle à la plaque**, à la verticale de son centre (un niveau à bulle posé sur le module aide).
2. Prendre une photo de test (depuis la racine du dépôt, sur le Pi) :
   ```
   python iot/capture.py --sortie iot/dataset/maquette/reglage --largeur 640 --hauteur 480 --nom reglage.jpg
   ```
   Résultat attendu :
   - **les 8 places et la zone interdite sont entièrement visibles**, avec la marge de 3 cm tout autour ;
   - les lignes des places sont **droites** (pas en trapèze : sinon l'objectif n'est pas vertical) ;
   - la **netteté** affichée par le script ne signale pas de flou.
3. Ajuster la hauteur et l'inclinaison, puis recommencer jusqu'à obtenir ce résultat.
4. **Bloquer la caméra** (serre-câbles, vis, ruban), puis marquer au crayon la position du support sur la table.
   À partir de là, on ne touche plus à la caméra. **Si elle bouge, il faut refaire la photo de calibration et redessiner les polygones.**
5. Éclairage : allumer les deux LED et éteindre le plafonnier si possible. Vérifier sur une photo qu'il n'y a **ni ombre portée de la caméra ni reflet** sur les voitures.

Résolution : **640 × 480** (environ 10 pixels par centimètre). C'est la résolution du contrat (§4.4) et de l'entraînement de YOLO.

---

## 4. Protocole des photos de référence

### 4.1 Règles pour toutes les photos

- **Personne dans le champ** : ni main, ni bras, ni visage (*privacy by design*). On place les voitures, **on sort du champ**, puis on photographie. `--intervalle 8` laisse le temps de se retirer.
- **On ne bouge pas la caméra.** Seule la série G (§4.2) la bouge exprès, en dernier.
- **Entre deux photos d'une même série, on change la position des voitures** : nombre de voitures, places, couleurs, légère rotation dans la place (± 10°), voiture plus ou moins centrée.
- Les photos restent **sur le disque, jamais dans Git** : dossier `iot/dataset/maquette/brut/<série>/`, ignoré par Git une fois la PR #9 fusionnée. En attendant, ne jamais faire `git add` sur `iot/dataset/`.
- Tenir un journal (paragraphe 4.3).

### 4.2 Les séries

| Série | Contenu | Nombre | Commande (sur le Pi, depuis la racine du dépôt) |
|---|---|---|---|
| **A. Vide** | Maquette sans voiture, éclairage normal. **Sert à la calibration des polygones** (`frontend/calibration.html`) | 5 | `python iot/capture.py --sortie iot/dataset/maquette/brut/A_vide --largeur 640 --hauteur 480 --nombre 5 --intervalle 3` |
| **B. Occupation normale** | De 1 à 8 voitures, chacune bien rangée dans une place, toutes les couleurs. Tous les remplissages : 1, 2, 4, 6, 8 voitures | 40 | `… --sortie iot/dataset/maquette/brut/B_normal --nombre 40 --intervalle 8` |
| **C. À cheval** | 1 ou 2 voitures **à cheval sur deux places** (A1/A2, A3/A4, B2/B3…), les autres rangées normalement | 20 | `… --sortie iot/dataset/maquette/brut/C_cheval --nombre 20 --intervalle 8` |
| **D. Zone interdite** | 1 voiture dans `allee1` (entière ou à moitié), d'autres dans les places | 15 | `… --sortie iot/dataset/maquette/brut/D_zone_interdite --nombre 15 --intervalle 8` |
| **E. Places spéciales** | A1 (PMR) et/ou B4 (électrique) occupées, avec d'autres voitures ailleurs | 10 | `… --sortie iot/dataset/maquette/brut/E_speciales --nombre 10 --intervalle 8` |
| **F. Éclairage** | Mêmes scènes que B, avec : une seule LED, LED baissée, lumière du jour en plus | 20 | `… --sortie iot/dataset/maquette/brut/F_eclairage --nombre 20 --intervalle 8` |
| **G. Angle** *(en dernier)* | Caméra **volontairement** décalée : ± 5 cm en hauteur, légère inclinaison (≤ 5°). **Remettre ensuite la caméra sur ses marques** et refaire 2 photos de la série A pour vérifier | 15 | `… --sortie iot/dataset/maquette/brut/G_angle --nombre 15 --intervalle 8` |
| **H. Cas difficiles** | Voitures très proches, voiture de couleur proche du fond (gris clair), voiture coupée au bord de l'image | 15 | `… --sortie iot/dataset/maquette/brut/H_difficiles --nombre 15 --intervalle 8` |
| | **Total** | **≈ 140** | |

`…` = `python iot/capture.py --largeur 640 --hauteur 480`.

Les séries C, D et E servent aussi à tester plus tard la **détection des anomalies** (`cheval`, `zone_interdite`, `place_speciale`). Leurs photos, rangées par série, deviendront des cas de test.

Répartition prévue (planning du sprint 3) : **lot 2** pour Eya, **lot 3** pour Amal, environ 70 photos chacune. Chaque lot contient toutes les séries, pour garder la variété.

### 4.3 Journal des prises de vue

Un fichier `iot/dataset/maquette/journal.csv` (non versionné), avec une ligne par série :

```
serie,date,heure,eclairage,hauteur_camera_cm,nb_photos,remarques
A_vide,2026-11-16,10:05,2 LED,62,5,
B_normal,2026-11-16,10:12,2 LED,62,40,voiture rouge dans A3 sur 6 photos
```

### 4.4 Contrôle après chaque série

1. Ouvrir 3 ou 4 photos au hasard. Vérifier qu'elles sont nettes, qu'aucune main ou personne n'est dans le champ et que tout le parking est visible.
2. Mesurer la qualité de la pré-annotation sur la série, avec le même script que pour le lot 1 (`iot/annotation/`, qui arrive avec la PR #9) :
   ```
   python iot/annotation/pre_annoter.py --entree iot/dataset/maquette/brut/B_normal --sortie iot/dataset/pre_annotations/maquette_B --limite 10
   ```
   Le résumé signale les images sans détection, à vérifier en priorité. Il s'agit seulement d'un contrôle : l'annotation se fait au sprint 3.

---

## 5. C'est fini quand…

- [ ] la maquette respecte le plan du contrat §4 (8 places de 8 × 13 cm, allée, `allee1`, PMR en A1, électrique en B4) ;
- [ ] la caméra est fixée, verticale, entre 55 et 70 cm, et sa position est marquée ;
- [ ] la photo de réglage montre tout le parking, net, sans ombre ni reflet ;
- [ ] les séries A à H sont prises (≈ 140 photos), sans personne dans le champ, et le journal est rempli ;
- [ ] une photo de la série A est transmise à Amal pour la calibration des polygones (`places.json`).

## 6. Ce qui dépend du matériel (rien de codable avant)

| Étape | Bloquée par | Sprint |
|---|---|---|
| Construction (§2) | Plaque, rubans, voitures miniatures | 2, ou 3 si le matériel n'arrive pas |
| Installation de la caméra (§3) | Raspberry Pi, caméra, support, LED | idem |
| Photos de référence (§4) | Maquette et caméra | idem |
| Calibration des polygones (`places.json`) | Photo de la série A, outil d'Amal | 3 |
| Lots 2 et 3 (annotation) | Photos des séries B à H | 3 |
