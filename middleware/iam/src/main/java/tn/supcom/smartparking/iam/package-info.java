/**
 * IAM (identité et accès) : comptes, hachage Argon2id des mots de passe, émission et vérification des JWT
 * (RS256, {@code kid}), publication des clés publiques (JWKS), rôles DRIVER et OPERATOR.
 *
 * <ul>
 *   <li>{@code api.auth}, {@code api.jwk} : routes JAX-RS seulement (register, login, me ; JWKS) ;</li>
 *   <li>{@code controllers} : lecture des requêtes et mise en forme des réponses ;</li>
 *   <li>{@code managers} : toute la logique ;</li>
 *   <li>{@code core.security}, {@code core.token}, {@code core.validation} : hachage, jetons, règles d'entrée ;</li>
 *   <li>{@code keyresolver} : les clés RSA par {@code kid} (rotation) ;</li>
 *   <li>{@code repository}, {@code model}, {@code exceptions} : comptes MongoDB, modèle, erreurs du contrat.</li>
 * </ul>
 * Module autonome : il ne dépend d'aucun autre module du projet. Voir middleware/iam/README.md.
 */
package tn.supcom.smartparking.iam;
