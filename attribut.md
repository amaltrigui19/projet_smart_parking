## 👥 1. Identification de l'Équipe
*   **Projet (Nom) :** Smart Parking — parking intelligent par vision par ordinateur
*   **Équipe (Numéro) :2
*   **Membres :**
    *   Membre 1 (Prénom, Nom) : Eya Kammoun
    *   Membre 2 (Prénom, Nom) : Amal Trigui
    *   Membre 3 (Prénom, Nom) : Maissa Ben Mrad

---

## 🧠 2. Usage des Assistants de Génération (IA)
*Cochez l'option qui correspond à votre réalité et détaillez le périmètre.*

*   [ ] **Option A :** Aucun outil de génération de code par IA n'a été utilisé.
*   [ ] **Option B :** Utilisation modérée (Aide au débogage, génération de fonctions isolées, documentation).
*   [x] **Option C :** Utilisation intensive (Génération de structures de dossiers, squelettes d'API complets, scripts de déploiement).

### 📝 Journal des invites (Prompts) notables et composants générés par IA :

| Composant / Module | Outil utilisé (ChatGPT/Claude/Copilot...) | Description du prompt ou du rôle confié à l'IA |
| :--- | :--- | :--- |
| Authentification PWA (connexion, inscription, redirection selon le rôle, déconnexion) | Claude | Génération du squelette des pages/formulaires de connexion et d'inscription, et de la logique de redirection selon le rôle utilisateur |
| Calibration des polygones (enregistrement/chargement via l'API, script de relecture pour la démo) | Claude | Aide à l'implémentation de l'enregistrement et du chargement des polygones via l'API, et génération du script de relecture pour la démo |
| Fausse API locale (mock GET/PUT /api/config/polygones) | Claude | Génération du squelette de l'API mock locale servant à tester la calibration sans dépendre du backend réel |
| Schéma MongoDB 2 (autorisations, réservations, événements : modèles, DAO, index, données d'exemple) | Claude | Génération des modèles et des DAO du module core à partir du contrat §2.14, des scripts index.js / exemples.js / verifier-schema.js et des tests unitaires ; vérification sur la pile Docker locale |

---

## 📚 3. Emprunts, Squelettes de Cours & Plagiat
*Précisez si vous avez utilisé une base de code préexistante.*

*   **Code de départ enseignant / squelette du cours :**
    *   [ ] Oui (indiquer quels modules : ________________________)
    *   [x] Non
*   **Réutilisation d'anciens projets (Années précédentes / GitHub externes) :**
    *   [ ] Oui (Lien du dépôt d'origine : `https://github.com...`)
    *   [x] Non
    *   *Si Oui, listez les fichiers ou concepts importés directement :* —

---

## 🛠️ 4. Répartition Réelle de l'Écriture du Code
*Indiquez la provenance réelle (en %) des lignes de code actuellement sur votre branche principale.*

| Module / Composant | % Écrit à la main par l'équipe | % Généré par IA | % Emprunté / Réutilisé |
| :--- | :---: | :---: | :---: |
| **Passerelle IoT / Node-RED / Edge** |30 % |70 % | 0 % |
| **Backend / API REST / Middleware** | 30 % |70 % | 0 % |
| **Sécurité (IAM / Keycloak / mTLS)** | 30 % | 70 % | 0 % |
| **Frontend / PWA** |30 % |70 % | 0 % |
| **Scripts d'Infra / Déploiement** | 30 % | 70 % | 0 % |

---

## ✍️ 5. Engagement sur l'Honneur
En validant ce fichier sur notre dépôt GitHub, nous certifions sur l'honneur que les déclarations ci-dessus sont exactes, sincères et reflètent fidèlement notre processus de développement. Nous sommes conscients que la falsification de ce document ou la non-maîtrise technique du code déclaré comme "écrit par l'équipe" lors des soutenances entraînera une **pénalité immédiate et sévère sur la note globale**.

*   [x ] **Lu et approuvé par l'ensemble des membres de l'équipe.**
*   **Date de dernière mise à jour :** 17 / 11 / 2026
