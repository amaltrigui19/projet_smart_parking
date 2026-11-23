package tn.supcom.smartparking.iam.model;

import java.time.Instant;

/**
 * Un compte, tel que stocké dans la collection MongoDB {@code users}.
 *
 * @param id           identifiant exposé par l'API : « u_ » + l'ObjectId en hexadécimal
 * @param passwordHash empreinte Argon2id au format PHC ; jamais renvoyée par l'API ni affichée
 */
public record User(String id, String name, String email, String passwordHash,
                   Role role, boolean active, Instant createdAt) {

    /** Préfixe des identifiants d'utilisateur dans l'API. */
    public static final String ID_PREFIX = "u_";

    /** Même compte avec une nouvelle empreinte (re-hachage après un changement des paramètres Argon2). */
    public User withPasswordHash(String newHash) {
        return new User(id, name, email, newHash, role, active, createdAt);
    }

    /** N'affiche jamais l'empreinte du mot de passe. */
    @Override
    public String toString() {
        return "User[id=" + id + ", email=" + email + ", role=" + role + ", active=" + active + "]";
    }
}
