package tn.supcom.smartparking.iam.managers;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import tn.supcom.smartparking.iam.keyresolver.KeyResolver;

/**
 * Construit le JWKS (RFC 7517) : la liste des clés <b>publiques</b> RSA, chacune avec son {@code kid},
 * {@code use=sig} et {@code alg=RS256}. Les services qui vérifient les jetons (l'API) choisissent la clé
 * d'après le {@code kid} de l'en-tête du jeton. Aucune partie privée n'est jamais publiée.
 */
@ApplicationScoped
public class JwksManager {

    @Inject
    KeyResolver keys;

    public JwksManager() {
        // Pour CDI
    }

    public JwksManager(KeyResolver keys) {
        this.keys = keys;
    }

    public JWKSet publicJwkSet() {
        List<JWK> jwks = new ArrayList<>();
        keys.publicKeys().forEach((kid, publicKey) -> jwks.add(new RSAKey.Builder(publicKey)
                .keyID(kid)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build()));
        return new JWKSet(jwks).toPublicJWKSet();
    }

    /** Le JWKS en JSON, prêt à être servi. */
    public String publicJwkSetJson() {
        return publicJwkSet().toString(true);
    }
}
