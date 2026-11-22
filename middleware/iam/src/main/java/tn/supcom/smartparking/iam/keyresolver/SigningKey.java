package tn.supcom.smartparking.iam.keyresolver;

import java.security.interfaces.RSAPrivateKey;
import java.util.Objects;

/**
 * Clé privée de signature et son identifiant {@code kid} (mis dans l'en-tête de chaque jeton).
 *
 * @param kid        identifiant publié dans le JWKS
 * @param privateKey clé privée RSA ; jamais affichée (voir {@link #toString()})
 */
public record SigningKey(String kid, RSAPrivateKey privateKey) {

    public SigningKey {
        Objects.requireNonNull(kid, "kid");
        Objects.requireNonNull(privateKey, "privateKey");
    }

    /** Ne montre jamais la clé, même dans un journal ou un message d'erreur. */
    @Override
    public String toString() {
        return "SigningKey[kid=" + kid + "]";
    }
}
