package tn.supcom.smartparking.iam.api.auth;

import tn.supcom.smartparking.iam.core.security.Secrets;

/** Corps de {@code POST /api/auth/login} : {@code {"email", "password"}}. */
public record LoginRequest(String email, char[] password) implements AutoCloseable {

    /** Efface le mot de passe de la mémoire. */
    @Override
    public void close() {
        Secrets.wipe(password);
    }

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + "]";
    }
}
