package tn.supcom.smartparking.iam.core.token;

import tn.supcom.smartparking.iam.exceptions.IamException;

/** Vérifie un jeton reçu (signature, kid, émetteur, expiration). */
public interface TokenValidator {

    /**
     * @return les revendications du jeton s'il est valide
     * @throws IamException 401 {@code unauthenticated} s'il est absent, falsifié, expiré ou signé par une clé inconnue
     */
    TokenClaims validate(String token);
}
