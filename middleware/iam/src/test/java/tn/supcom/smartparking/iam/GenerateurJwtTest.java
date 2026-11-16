package tn.supcom.smartparking.iam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GenerateurJwtTest {

    private static KeyPair cles;
    private static final Instant MAINTENANT = Instant.parse("2026-11-20T10:00:00Z");
    private static final Utilisateur SARA = new Utilisateur("u_6543a1f0aaaaaaaaaaaaaaaa", "Sara Ben Ali",
            "sara@exemple.tn", "x", Role.CONDUCTEUR, true, MAINTENANT);

    @BeforeAll
    static void genererCles() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        cles = g.generateKeyPair();
    }

    private static GenerateurJwt generateur() {
        return new GenerateurJwt(cles.getPrivate(), GenerateurJwt.EMETTEUR, GenerateurJwt.DUREE_VIE,
                Clock.fixed(MAINTENANT, ZoneOffset.UTC));
    }

    private static JsonObject partie(String jeton, int i) {
        String json = new String(Base64.getUrlDecoder().decode(jeton.split("\\.")[i]), StandardCharsets.UTF_8);
        return Json.createReader(new StringReader(json)).readObject();
    }

    private static boolean signatureValide(String jeton, PublicKey cle) throws Exception {
        String[] p = jeton.split("\\.");
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initVerify(cle);
        s.update((p[0] + "." + p[1]).getBytes(StandardCharsets.US_ASCII));
        return s.verify(Base64.getUrlDecoder().decode(p[2]));
    }

    @Test
    void enteteRs256() {
        JsonObject entete = partie(generateur().emettre(SARA), 0);
        assertEquals("RS256", entete.getString("alg"));
        assertEquals("JWT", entete.getString("typ"));
    }

    @Test
    void revendicationsDuContrat() {
        JsonObject c = partie(generateur().emettre(SARA), 1);
        assertEquals("smartparking", c.getString("iss"));
        assertEquals("u_6543a1f0aaaaaaaaaaaaaaaa", c.getString("sub"));
        assertEquals("sara@exemple.tn", c.getString("upn"));
        assertEquals("CONDUCTEUR", c.getString("role"));
        assertEquals("CONDUCTEUR", c.getJsonArray("groups").getString(0));
        assertEquals(MAINTENANT.getEpochSecond(), c.getJsonNumber("iat").longValue());
        assertEquals(3600, c.getJsonNumber("exp").longValue() - c.getJsonNumber("iat").longValue());
        assertTrue(c.containsKey("jti"));
        assertFalse(c.containsKey("mot_de_passe_hash"));
    }

    @Test
    void roleExploitant() {
        Utilisateur expl = new Utilisateur("u_1", "E", "e@x.tn", "x", Role.EXPLOITANT, true, MAINTENANT);
        JsonObject c = partie(generateur().emettre(expl), 1);
        assertEquals("EXPLOITANT", c.getString("role"));
        assertEquals("EXPLOITANT", c.getJsonArray("groups").getString(0));
    }

    @Test
    void signatureVerifieeAvecLaClePublique() throws Exception {
        assertTrue(signatureValide(generateur().emettre(SARA), cles.getPublic()));
    }

    @Test
    void jetonModifieRefuse() throws Exception {
        String[] p = generateur().emettre(SARA).split("\\.");
        String faux = Base64.getUrlEncoder().withoutPadding().encodeToString(
                partie(String.join(".", p), 1).toString().replace("CONDUCTEUR", "EXPLOITANT")
                        .getBytes(StandardCharsets.UTF_8));
        assertFalse(signatureValide(p[0] + "." + faux + "." + p[2], cles.getPublic()));
    }

    @Test
    void autreClePubliqueRefusee() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        assertFalse(signatureValide(generateur().emettre(SARA), g.generateKeyPair().getPublic()));
    }

    @Test
    void lectureDUneClePem() throws Exception {
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(cles.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        GenerateurJwt g = new GenerateurJwt(GenerateurJwt.lireClePrivee(pem));
        assertTrue(signatureValide(g.emettre(SARA), cles.getPublic()));
        assertEquals(3600, g.dureeVieSecondes());
    }

    @Test
    void clePemInvalideRefusee() {
        assertThrows(IllegalArgumentException.class, () -> GenerateurJwt.lireClePrivee("pas une clé"));
        assertThrows(IllegalArgumentException.class, () -> GenerateurJwt.lireClePrivee(
                "-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----"));
    }
}
