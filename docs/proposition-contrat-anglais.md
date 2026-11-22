# Proposition : contrat en anglais

Rédaction : Amal · Statut : **proposition, à valider par Eya et Maissa**

Suite aux remarques du prof, tous les noms techniques passent en anglais : routes, champs JSON, valeurs, codes d'erreur, collections MongoDB, topics MQTT et variables d'environnement. Ce document donne la correspondance **ancien nom → nouveau nom**. Une fois validé, chacune applique les nouveaux noms dans sa partie, puis `docs/contrats.md` est réécrit avec ces noms.

**Ce qui ne change pas :**
- les identifiants de données : parking `P1`, places `A1`…`B4`, nœuds `E`, `I1`…`I4`, `S`, zone `allee1` ;
- les préfixes d'identifiants `u_`, `r_`, `evt_` ;
- les unités (`_cm`, `_min`, `_s`), le format des dates (ISO 8601 UTC), `Authorization: Bearer <jwt>` ;
- le texte des champs `message`, `title` et `body` : il est affiché aux utilisateurs et reste en français.

---

## 1. Rôles

| Avant | Après |
|---|---|
| `CONDUCTEUR` | `DRIVER` |
| `EXPLOITANT` | `OPERATOR` |

Utilisés dans le JWT (`role`, `groups`), dans `@RolesAllowed` et dans la base.

## 2. Routes REST

| Avant | Après | Propriétaire |
|---|---|---|
| `GET /api/sante` | `GET /api/health` | Amal |
| `POST /api/auth/inscription` | `POST /api/auth/register` | Maissa (IAM) |
| `POST /api/auth/connexion` | `POST /api/auth/login` | Maissa (IAM) |
| `GET /api/auth/moi` | `GET /api/auth/me` | Maissa (IAM) |
| *(nouveau)* | `GET /.well-known/jwks.json` | Maissa (IAM) |
| `GET /api/parking` | `GET /api/parking` *(inchangé)* | Amal |
| `GET /api/places` | `GET /api/spots` | Amal |
| `GET /api/places/{id}` | `GET /api/spots/{id}` | Amal |
| `WS /api/ws/places?token=` | `WS /api/ws/spots?token=` | Amal |
| `POST, GET, DELETE /api/guidage` | `POST, GET, DELETE /api/guidance` | Amal |
| `POST /api/guidage/arrivee` | `POST /api/guidance/arrival` | Amal |
| `/api/reservations…` | `/api/reservations…` *(inchangé)* | Amal |
| `POST /api/reservations/{id}/paiement` | `POST /api/reservations/{id}/payment` | Amal |
| `GET /api/tableau-de-bord` | `GET /api/dashboard` | Amal |
| `GET /api/evenements` | `GET /api/events` | Amal |
| `PATCH /api/evenements/{id}` | `PATCH /api/events/{id}` | Amal |
| `GET, PUT /api/config/polygones` | `GET, PUT /api/config/polygons` | Amal |
| `GET, PUT /api/config/plan` | `GET, PUT /api/config/layout` | Amal |
| `PATCH /api/config/places/{id}` | `PATCH /api/config/spots/{id}` | Amal |
| `GET, PATCH /api/config/parametres` | `GET, PATCH /api/config/settings` | Amal |
| `GET, POST /api/utilisateurs` | `GET, POST /api/users` | Maissa (IAM) |
| `PATCH /api/utilisateurs/{id}` | `PATCH /api/users/{id}` | Maissa (IAM) |
| `POST /api/utilisateurs/{id}/autorisations` | `POST /api/users/{id}/permissions` | Maissa (IAM) |
| `DELETE /api/utilisateurs/{id}/autorisations/{aid}` | `DELETE /api/users/{id}/permissions/{pid}` | Maissa (IAM) |
| `GET /api/stats` | `GET /api/stats` *(inchangé)* | Amal |
| `GET /api/push/cle-publique` | `GET /api/push/public-key` | Amal |
| `POST, DELETE /api/push/abonnements` | `POST, DELETE /api/push/subscriptions` | Amal |

Paramètres de requête : `etat` → `state`, `limite` → `limit`, `utilisateur` → `user`, `traite` → `handled`, `du` / `au` → `from` / `to`, `granularite` → `granularity`, `actif` → `active` (`type` et `role` inchangés).

## 3. Erreurs

Corps : `{"erreur": "...", "message": "..."}` → **`{"error": "...", "message": "..."}`**

| Avant | Après |
|---|---|
| `requete_invalide` | `invalid_request` |
| `non_authentifie` | `unauthenticated` |
| `acces_refuse` | `forbidden` |
| `introuvable` | `not_found` |
| `erreur_serveur` | `server_error` |
| `methode_non_permise` | `method_not_allowed` |
| `type_non_supporte` | `unsupported_media_type` |
| `config_invalide` | `invalid_config` |
| `email_existant` | `email_already_exists` |
| `identifiants_invalides` | `invalid_credentials` |
| `compte_desactive` | `account_disabled` |
| `parking_complet` | `parking_full` |
| `place_indisponible` | `spot_unavailable` |
| `reservation_deja_active` | `reservation_already_active` |
| `autorisation_requise` | `permission_required` |
| `annulation_impossible` | `cancellation_not_allowed` |
| `deja_payee` | `already_paid` |
| `paiement_impossible` | `payment_not_allowed` |
| `action_interdite` | `action_not_allowed` |
| `autorisation_existante` | `permission_already_exists` |

## 4. Valeurs (énumérations)

| Contexte | Avant | Après |
|---|---|---|
| État d'une place | `libre`, `occupee`, `reservee` | `free`, `occupied`, `reserved` |
| Type de place | `normale`, `pmr`, `electrique` | `standard`, `accessible`, `electric` |
| Anomalie | `cheval`, `ventouse`, `place_speciale`, `zone_interdite` | `straddling`, `overstay`, `unauthorized_special_spot`, `forbidden_zone` |
| État d'une réservation | `active`, `en_cours`, `terminee`, `payee`, `annulee`, `expiree` | `active`, `in_progress`, `completed`, `paid`, `cancelled`, `expired` |
| Moyen de paiement | `simule` | `simulated` |
| Source d'un événement | `pi`, `serveur` | `pi`, `server` |
| Granularité des stats | `heure`, `jour` | `hour`, `day` |
| Type de nœud du graphe | `entree`, `intersection`, `sortie`, `place` | `entrance`, `intersection`, `exit`, `spot` |
| Type d'autorisation | `pmr`, `electrique` | `accessible`, `electric` |

## 5. Champs JSON

Même nom partout où le champ apparaît : API, WebSocket, MQTT, push et MongoDB.

| Avant | Après | | Avant | Après |
|---|---|---|---|---|
| `nom` | `name` | | `mot_de_passe` | `password` |
| `jeton` | `access_token` | | `expire_dans` | `expires_in` |
| `utilisateur` | `user` | | `autorisations` | `permissions` |
| `actif` | `active` | | `cree_le` | `created_at` |
| `place` | `spot` | | `places` | `spots` |
| `etat` | `state` | | `depuis` | `since` |
| `duree_max_min` | `max_duration_min` | | `poly` | `polygon` |
| `zones_interdites` | `forbidden_zones` | | `noeud` | `node` |
| `resume` | `summary` | | `libres` / `occupees` / `reservees` | `free` / `occupied` / `reserved` |
| `depassement` | `overdue` | | `verrou` | `lock` |
| `jusqu_a` | `until` | | `verrou_jusqu_a` | `locked_until` |
| `depart` | `from` | | `trajet` | `path` |
| `confirme_le` | `confirmed_at` | | `creee_le` | `created_at` |
| `expire_le` | `expires_at` | | `debut` / `fin` | `start` / `end` |
| `montant` | `amount` | | `devise` | `currency` |
| `paiement` | `payment` | | `moyen` | `method` |
| `paye_le` | `paid_at` | | `recu` | `receipt` |
| `taux_occupation` | `occupancy_rate` | | `alertes_non_traitees` | `unhandled_alerts` |
| `en_ligne` | `online` | | `temp_cpu` | `cpu_temp` |
| `recu_le` | `received_at` | | `traite` | `handled` |
| `traite_par` | `handled_by` | | `traite_le` | `handled_at` |
| `commentaire` | `comment` | | `duree_min` | `duration_min` |
| `statut` | `status` | | `maj_le` | `updated_at` |
| `graphe` | `graph` | | `noeuds` / `aretes` | `nodes` / `edges` |
| `de` / `a` (arêtes) | `from` / `to` | | `longueur_cm` | `length_cm` |
| `tarif_horaire` | `hourly_rate` | | `verrou_min` | `lock_min` |
| `confirmation_arrivee_s` | `arrival_confirmation_s` | | `delai_reservation_min` | `reservation_timeout_min` |
| `preavis_fin_duree_min` | `end_warning_min` | | `pi_hors_ligne_min` | `pi_offline_min` |
| `parametres` | `settings` | | `accordee_par` | `granted_by` |
| `taux_occupation_moyen` | `average_occupancy_rate` | | `duree_moyenne_min` | `average_duration_min` |
| `occupation` | `occupancy` | | `taux` | `rate` |
| `heures_de_pointe` | `peak_hours` | | `heure` | `hour` |
| `par_place` | `per_spot` | | `infractions` | `violations` |
| `titre` | `title` | | `corps` | `body` |
| `donnees` | `data` | | `cle` / `cles` | `key` / `keys` |
| `evenement` | `event` | | `ancienne_place` | `previous_spot` |
| `fin_prevue` | `expected_end` | | `type_place` | `spot_type` |
| `mot_de_passe_hash` | `password_hash` | | `du` / `au` | `from` / `to` |

Inchangés : `id`, `type`, `email`, `role`, `parking`, `resolution`, `dimensions_cm`, `rect_cm`, `zone`, `source`, `details`, `fps`, `ts`, `version`, `total`, `distance_cm`, `endpoint`, `url`, `x`, `y`, `mongo`, `mqtt`.

Préfixe des identifiants d'autorisation : `aut_…` → `perm_…`.

## 6. Notifications push (types)

| Avant | Après |
|---|---|
| `reservation_confirmee` | `reservation_confirmed` |
| `fin_duree` | `time_almost_up` |
| `place_a_liberer` | `spot_overdue` |
| `place_prise` | `spot_taken` |
| anomalies | mêmes noms qu'en §4 |

Les `url` (`/conducteur#…`, `/exploitant#…`) suivront la séparation front office / back office décidée par Eya.

## 7. Collections MongoDB

| Avant | Après |
|---|---|
| `parkings` | `parkings` *(inchangé)* |
| `places` | `spots` |
| `utilisateurs` | `users` |
| `autorisations` | `permissions` |
| `reservations` | `reservations` *(inchangé)* |
| `evenements` | `events` |
| `abonnements_push` | `push_subscriptions` |
| `historique_occupation` | `occupancy_history` |

Les champs suivent le §5. `password_hash` est calculé avec **Argon2id** (et non plus PBKDF2).
Il faudra mettre à jour `server/mongo/seed.js` et recharger les données d'exemple.

## 8. MQTT

| Avant | Après |
|---|---|
| `parking/P1/places/<id>` | `parking/P1/spots/<id>` |
| `parking/P1/evenements` | `parking/P1/events` |
| `parking/P1/pi/statut` | `parking/P1/pi/status` |
| `parking/P1/config` | `parking/P1/config` *(inchangé)* |
| compte broker `serveur` | `server` |

Exemple de message après renommage : `{"spot":"A3","state":"occupied","since":"2026-11-20T10:02:11Z"}`.
Le fichier `places.json` suit le §5 : `parking`, `resolution`, `spots[id, type, max_duration_min, polygon]`, `forbidden_zones[id, polygon]`. Cela concerne aussi le code du Pi (`iot/`), l'outil de calibration et les scripts `frontend/outils/`.

## 9. Variables d'environnement

| Avant | Après |
|---|---|
| `DOMAINE` | `DOMAIN` |
| `MONGO_HOTE` | `MONGO_HOST` |
| `MONGO_PORT` | `MONGO_PORT` *(inchangé)* |
| `MONGO_PORT_HOTE` | `MONGO_HOST_PORT` |
| `MONGO_UTILISATEUR` | `MONGO_USER` |
| `MONGO_MOT_DE_PASSE` | `MONGO_PASSWORD` |
| `MONGO_BASE` | `MONGO_DATABASE` |
| `MQTT_HOTE` | `MQTT_HOST` |
| `MQTT_MDP_PI` | `MQTT_PASSWORD_PI` |
| `MQTT_MDP_SERVEUR` | `MQTT_PASSWORD_SERVER` |
| `EXPLOITANT_EMAIL` | `OPERATOR_EMAIL` |
| `EXPLOITANT_MOT_DE_PASSE` | `OPERATOR_PASSWORD` |
| `JWT_CLE_PRIVEE` | `JWT_PRIVATE_KEY` |
| `JWT_CLE_PRIVEE_FICHIER` | `JWT_PRIVATE_KEY_FILE` |
| `VAPID_CLE_PUBLIQUE` | `VAPID_PUBLIC_KEY` |
| `VAPID_CLE_PRIVEE` | `VAPID_PRIVATE_KEY` |
| `VAPID_SUJET` | `VAPID_SUBJECT` |
| *(nouveau)* | `SMARTPARKING_PARKING_ID`, `SMARTPARKING_PARKING_SPOT_IDS` (facultatifs, `P1` et `A1,…,B4` par défaut) |

## 10. JWT

- Contenu inchangé (`iss`, `sub`, `upn`, `role`, `groups`, `iat`, `exp`, `jti`), avec les rôles du §1.
- Ajout du `kid` dans l'en-tête du jeton. L'API récupère la clé publique sur `GET /.well-known/jwks.json` au lieu d'un fichier `publique.pem`.

---

## Qui applique quoi après validation

| Qui | Ce qu'elle renomme |
|---|---|
| **Amal** | Routes API métier, champs JSON et MongoDB de `core`, MQTT côté serveur, `iot/` (topics et `places.json`), `seed.js` |
| **Maissa** | Routes `/api/auth/*` et `/api/users/*`, JWT (`kid`, rôles), JWKS, collection `users` |
| **Eya** | Frontend (appels API, champs, valeurs), variables d'environnement (`server/`, `.env.example`), scripts `frontend/outils/` |

## Points à trancher ensemble

1. `pmr` → `accessible` ou `disabled` ? (proposition : `accessible`)
2. `/api/config/plan` → `/api/config/layout` ou `/api/config/floor-plan` ? (proposition : `layout`)
3. Garder les messages d'erreur (`message`) en français pour les utilisateurs ? (proposition : oui)
4. Ordre de bascule : tout le monde le même jour (proposition), ou routes anciennes et nouvelles en parallèle pendant un sprint.
