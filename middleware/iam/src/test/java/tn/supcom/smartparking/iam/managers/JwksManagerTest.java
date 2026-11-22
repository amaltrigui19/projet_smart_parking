package tn.supcom.smartparking.iam.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tn.supcom.smartparking.iam.TestKeys;
import tn.supcom.smartparking.iam.TestKeys.MutableClock;
import tn.supcom.smartparking.iam.core.token.JwtTokenIssuer;
import tn.supcom.smartparking.iam.keyresolver.FileKeyResolver;
import tn.supcom.smartparking.iam.keyresolver.KeyResolver;
import tn.supcom.smartparking.iam.model.Role;
import tn.supcom.smartparking.iam.model.User;

class JwksManagerTest {

    @Test
    void publieToutesLesClesPubliquesAvecKidSansPartiePrivee() throws Exception {
        KeyPair a = TestKeys.rsa();
        KeyPair b = TestKeys.rsa();
        Map<String, RSAPublicKey> publiques = new LinkedHashMap<>();
        publiques.put("2026-10", (RSAPublicKey) a.getPublic());
        publiques.put("2026-11", (RSAPublicKey) b.getPublic());
        KeyResolver cles = mock(KeyResolver.class);
        when(cles.publicKeys()).thenReturn(publiques);

        String json = new JwksManager(cles).publicJwkSetJson();
        JWKSet jwks = JWKSet.parse(json);

        assertEquals(2, jwks.getKeys().size());
        RSAKey k = (RSAKey) jwks.getKeyByKeyId("2026-11");
        assertEquals("RSA", k.getKeyType().getValue());
        assertEquals("sig", k.getKeyUse().getValue());
        assertEquals("RS256", k.getAlgorithm().getName());
        assertEquals(b.getPublic(), k.toRSAPublicKey());
        assertFalse(k.isPrivate());
        assertFalse(json.contains("\"d\""), "aucune partie privée dans le JWKS");
        assertFalse(json.contains("\"p\""));
    }

    @Test
    void unJetonSeVerifieAvecLaCleDuJwksChoisieParKid() throws Exception {
        KeyPair cle = TestKeys.rsa();
        KeyResolver cles = FileKeyResolver.ofPem(TestKeys.privatePem(cle), "2026-11");
        User u = new User("u_1", "Sara", "sara@exemple.tn", "h", Role.OPERATOR, true, Instant.now());
        SignedJWT jwt = SignedJWT.parse(new JwtTokenIssuer(cles, "smartparking", Duration.ofHours(1),
                new MutableClock(Instant.now())).issue(u).accessToken());

        // Ce que fera l'API : lire le JWKS publié, prendre la clé du kid, vérifier la signature.
        JWKSet publie = JWKSet.parse(new JwksManager(cles).publicJwkSetJson());
        RSAKey jwk = (RSAKey) publie.getKeyByKeyId(jwt.getHeader().getKeyID());
        assertTrue(jwt.verify(new com.nimbusds.jose.crypto.RSASSAVerifier(jwk)));
    }
}
