package tn.supcom.smartparking.iam.api.auth;

import tn.supcom.smartparking.iam.core.security.Secrets;

/**
 * Corps de {@code POST /api/auth/register} : {@code {"name", "email", "password"}}.
 * Un champ {@code role} éventuellement envoyé est ignoré (l'inscription crée toujours un DRIVER).
 */
public record RegisterRequest(String name, String email, char[] password) implements AutoCloseable {

    /** Efface le mot de passe de la mémoire. */
    @Override
    public void close() {
        Secrets.wipe(password);
    }

    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + "]";
    }
}
