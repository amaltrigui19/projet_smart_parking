package tn.supcom.smartparking.iam;

import java.time.Instant;

/**
 * Un compte, tel que stocké dans la collection MongoDB {@code utilisateurs} (contrat §2.14).
 *
 * @param id             identifiant tel qu'exposé par l'API : « u_ » + l'ObjectId en hexadécimal
 * @param motDePasseHash empreinte PBKDF2 (voir {@link HachageMotDePasse}), jamais renvoyée par l'API
 */
public record Utilisateur(String id, String nom, String email, String motDePasseHash,
                          Role role, boolean actif, Instant creeLe) {

    /** Préfixe des identifiants d'utilisateur dans l'API (contrat §2.1). */
    public static final String PREFIXE_ID = "u_";
}
