package tn.supcom.smartparking.iam.keyresolver;

import java.security.interfaces.RSAPublicKey;
import java.util.Map;
import java.util.Optional;

/**
 * Source des clés RSA des JWT, identifiées par leur {@code kid}.
 *
 * <p>Plusieurs clés peuvent coexister pour la rotation : une seule signe les nouveaux jetons
 * ({@link #signingKey()}), mais toutes celles qui sont publiées restent valables pour vérifier
 * les jetons déjà émis ({@link #resolve(String)}) et apparaissent dans le JWKS ({@link #publicKeys()}).
 */
public interface KeyResolver {

    /** Clé publique correspondant à ce {@code kid}, ou vide si elle est inconnue (jeton refusé). */
    Optional<RSAPublicKey> resolve(String kid);

    /** Clé privée active, avec son {@code kid}, pour signer les nouveaux jetons. */
    SigningKey signingKey();

    /** Toutes les clés publiques publiées, par {@code kid} (contenu du JWKS). */
    Map<String, RSAPublicKey> publicKeys();
}
