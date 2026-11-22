package tn.supcom.smartparking.iam.core.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tn.supcom.smartparking.iam.TestKeys;
import tn.supcom.smartparking.iam.TestKeys.MutableClock;
import tn.supcom.smartparking.iam.exceptions.IamException;
import tn.supcom.smartparking.iam.keyresolver.FileKeyResolver;
import tn.supcom.smartparking.iam.keyresolver.KeyResolver;
import tn.supcom.smartparking.iam.model.Role;
import tn.supcom.smartparking.iam.model.User;

class JwtTokenTest {

    static KeyPair cle;
    static KeyPair autreCle;
    static KeyResolver cles;
    static final Instant T0 = Instant.parse("2026-11-22T10:00:00Z");
    static final User SARA = new User("u_6543a1f0aa", "Sara", "sara@exemple.tn", "$argon2id$…", Role.DRIVER, true, T0);

    MutableClock horloge = new MutableClock(T0);

    @BeforeAll
    static void preparer() {
        cle = TestKeys.rsa();
        autreCle = TestKeys.rsa();
        cles = FileKeyResolver.ofPem(TestKeys.privatePem(cle), "2026-11");
    }

    private JwtTokenIssuer emetteur() {
        return new JwtTokenIssuer(cles, "smartparking", Duration.ofHours(1), horloge);
    }

    private JwtTokenValidator verificateur() {
        return new JwtTokenValidator(cles, "smartparking", horloge);
    }

    private static void refuse(Runnable r) {
        IamException e = assertThrows(IamException.class, r::run);
        assertEquals(401, e.status());
        assertEquals("unauthenticated", e.code());
    }

    @Test
    void jetonEmisPuisValideAvecToutesLesRevendications() throws Exception {
        IssuedToken t = emetteur().issue(SARA);
        assertEquals(3600, t.expiresIn());

        SignedJWT jwt = SignedJWT.parse(t.accessToken());
        assertEquals(JWSAlgorithm.RS256, jwt.getHeader().getAlgorithm());
        assertEquals(JOSEObjectType.JWT, jwt.getHeader().getType());
        assertEquals("2026-11", jwt.getHeader().getKeyID());
        JWTClaimsSet c = jwt.getJWTClaimsSet();
        assertEquals("smartparking", c.getIssuer());
        assertEquals("u_6543a1f0aa", c.getSubject());
        assertEquals("sara@exemple.tn", c.getStringClaim("upn"));
        assertEquals("DRIVER", c.getStringClaim("role"));
        assertEquals(List.of("DRIVER"), c.getStringListClaim("groups"));
        assertEquals(T0.getEpochSecond(), c.getIssueTime().toInstant().getEpochSecond());
        assertEquals(T0.plusSeconds(3600).getEpochSecond(), c.getExpirationTime().toInstant().getEpochSecond());
        assertEquals(36, c.getJWTID().length());

        TokenClaims v = verificateur().validate(t.accessToken());
        assertEquals("2026-11", v.kid());
        assertEquals("u_6543a1f0aa", v.subject());
        assertEquals(Role.DRIVER, v.role());
        assertNotEquals(c.getJWTID(), SignedJWT.parse(emetteur().issue(SARA).accessToken()).getJWTClaimsSet().getJWTID());
    }

    @Test
    void chargeModifieeRefusee() {
        String[] p = emetteur().issue(SARA).accessToken().split("\\.");
        String json = new String(Base64.getUrlDecoder().decode(p[1]), StandardCharsets.UTF_8).replace("DRIVER", "OPERATOR");
        String falsifie = p[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8)) + "." + p[2];
        refuse(() -> verificateur().validate(falsifie));
    }

    @Test
    void signatureDUneAutreCleAvecLeMemeKidRefusee() throws Exception {
        String j1 = signer((RSAPrivateKey) autreCle.getPrivate(), "2026-11", revendications().build());
        refuse(() -> verificateur().validate(j1));
    }

    @Test
    void kidInconnuOuAbsentRefuse() throws Exception {
        String j2 = signer((RSAPrivateKey) cle.getPrivate(), "2026-01", revendications().build());
        refuse(() -> verificateur().validate(j2));
        String j3 = signer((RSAPrivateKey) cle.getPrivate(), null, revendications().build());
        refuse(() -> verificateur().validate(j3));
    }

    @Test
    void jetonExpireRefuseApresLaTolerance() {
        String t = emetteur().issue(SARA).accessToken();
        horloge.advance(Duration.ofHours(1).plusSeconds(20));
        verificateur().validate(t); // dans les 30 s de tolérance
        horloge.advance(Duration.ofSeconds(20));
        refuse(() -> verificateur().validate(t));
    }

    @Test
    void mauvaisEmetteurRefuse() {
        String t = new JwtTokenIssuer(cles, "pirate", Duration.ofHours(1), horloge).issue(SARA).accessToken();
        refuse(() -> verificateur().validate(t));
    }

    @Test
    void roleOuSujetInvalidesRefuses() throws Exception {
        String j4 = signer((RSAPrivateKey) cle.getPrivate(), "2026-11",
                revendications().claim("role", "ADMIN").build());
        refuse(() -> verificateur().validate(j4));
        String j5 = signer((RSAPrivateKey) cle.getPrivate(), "2026-11",
                revendications().subject("admin").build());
        refuse(() -> verificateur().validate(j5));
    }

    @Test
    void algNoneEtHs256Refuses() throws Exception {
        refuse(() -> verificateur().validate(new PlainJWT(revendications().build()).serialize()));
        SignedJWT hs = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID("2026-11").build(), revendications().build());
        hs.sign(new MACSigner("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
        refuse(() -> verificateur().validate(hs.serialize()));
    }

    @Test
    void jetonVideOuIllisibleRefuse() {
        refuse(() -> verificateur().validate(null));
        refuse(() -> verificateur().validate(" "));
        refuse(() -> verificateur().validate("a.b.c"));
    }

    @Test
    void toStringNAfficheJamaisLeJeton() {
        assertEquals("IssuedToken[expiresIn=3600]", emetteur().issue(SARA).toString());
    }

    private JWTClaimsSet.Builder revendications() {
        return new JWTClaimsSet.Builder().issuer("smartparking").subject("u_1").claim("upn", "x@exemple.tn")
                .claim("role", "DRIVER").issueTime(Date.from(T0)).expirationTime(Date.from(T0.plusSeconds(3600)));
    }

    private static String signer(RSAPrivateKey privee, String kid, JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build(), claims);
        jwt.sign(new RSASSASigner(privee));
        return jwt.serialize();
    }
}
