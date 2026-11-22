package tn.supcom.smartparking.iam.keyresolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tn.supcom.smartparking.iam.TestKeys;
import tn.supcom.smartparking.iam.TestKeys.MutableClock;

class FileKeyResolverTest {

    static KeyPair octobre;
    static KeyPair novembre;
    static KeyPair ancienne;

    @TempDir
    Path dir;

    @BeforeAll
    static void cles() {
        octobre = TestKeys.rsa();
        novembre = TestKeys.rsa();
        ancienne = TestKeys.rsa();
    }

    private void ecrire(String nom, String contenu) throws IOException {
        Files.writeString(dir.resolve(nom), contenu);
    }

    @Test
    void uneSeuleCleAvecKid() {
        FileKeyResolver r = FileKeyResolver.ofPem(TestKeys.privatePem(octobre), "cle-1");
        assertEquals("cle-1", r.signingKey().kid());
        assertEquals(octobre.getPublic(), r.resolve("cle-1").orElseThrow());
        assertEquals(List.of("cle-1"), List.copyOf(r.publicKeys().keySet()));
    }

    @Test
    void uneSeuleCleSansKidUtiliseLEmpreinteRfc7638() {
        FileKeyResolver r = FileKeyResolver.ofPem(TestKeys.privatePem(octobre), null);
        String kid = r.signingKey().kid();
        assertEquals(43, kid.length()); // SHA-256 en base64url sans remplissage
        assertEquals(kid, FileKeyResolver.ofPem(TestKeys.privatePem(octobre), null).signingKey().kid()); // stable
        assertFalse(kid.equals(FileKeyResolver.ofPem(TestKeys.privatePem(novembre), null).signingKey().kid()));
    }

    @Test
    void dossierPlusieursClesLaDerniereSigne() throws IOException {
        ecrire("2026-10.pem", TestKeys.privatePem(octobre));
        ecrire("2026-11.pem", TestKeys.privatePem(novembre));
        ecrire("2026-09.pem", TestKeys.publicPem(ancienne)); // ancienne clé : publiée, ne signe plus
        ecrire("notes.txt", "ignoré");
        FileKeyResolver r = FileKeyResolver.ofDirectory(dir, null, new MutableClock(Instant.now()));

        assertEquals("2026-11", r.signingKey().kid());
        assertEquals(List.of("2026-09", "2026-10", "2026-11"), List.copyOf(r.publicKeys().keySet()));
        assertEquals(ancienne.getPublic(), r.resolve("2026-09").orElseThrow());
        assertEquals(octobre.getPublic(), r.resolve("2026-10").orElseThrow());
        assertTrue(r.resolve("2026-08").isEmpty());
        assertTrue(r.resolve(null).isEmpty());
    }

    @Test
    void cleActiveChoisieParJwtActiveKid() throws IOException {
        ecrire("2026-10.pem", TestKeys.privatePem(octobre));
        ecrire("2026-11.pem", TestKeys.privatePem(novembre));
        FileKeyResolver r = FileKeyResolver.ofDirectory(dir, "2026-10", new MutableClock(Instant.now()));
        assertEquals("2026-10", r.signingKey().kid());
    }

    @Test
    void cleActiveInconnueOuPubliqueRefusee() throws IOException {
        ecrire("2026-09.pem", TestKeys.publicPem(ancienne));
        ecrire("2026-10.pem", TestKeys.privatePem(octobre));
        assertThrows(IllegalStateException.class,
                () -> FileKeyResolver.ofDirectory(dir, "2026-12", new MutableClock(Instant.now())).signingKey());
        assertThrows(IllegalStateException.class,
                () -> FileKeyResolver.ofDirectory(dir, "2026-09", new MutableClock(Instant.now())).signingKey());
    }

    @Test
    void dossierSansClePriveeOuAbsent() throws IOException {
        ecrire("2026-09.pem", TestKeys.publicPem(ancienne));
        assertThrows(IllegalStateException.class,
                () -> FileKeyResolver.ofDirectory(dir, null, new MutableClock(Instant.now())).signingKey());
        assertThrows(IllegalStateException.class,
                () -> FileKeyResolver.ofDirectory(dir.resolve("absent"), null, new MutableClock(Instant.now())).signingKey());
    }

    @Test
    void nomDeFichierInvalideRefuse() throws IOException {
        ecrire("clé avec espaces.pem", TestKeys.privatePem(octobre));
        assertThrows(IllegalStateException.class,
                () -> FileKeyResolver.ofDirectory(dir, null, new MutableClock(Instant.now())).signingKey());
    }

    @Test
    void rotationSansRedemarrageApres60Secondes() throws IOException {
        ecrire("2026-10.pem", TestKeys.privatePem(octobre));
        MutableClock horloge = new MutableClock(Instant.parse("2026-11-22T10:00:00Z"));
        FileKeyResolver r = FileKeyResolver.ofDirectory(dir, null, horloge);
        assertEquals("2026-10", r.signingKey().kid());

        ecrire("2026-11.pem", TestKeys.privatePem(novembre)); // nouvelle clé déposée
        horloge.advance(Duration.ofSeconds(30));
        assertEquals("2026-10", r.signingKey().kid()); // pas encore relu
        horloge.advance(Duration.ofSeconds(31));
        assertEquals("2026-11", r.signingKey().kid());
        assertTrue(r.resolve("2026-10").isPresent()); // l'ancienne reste valable pour vérifier
    }

    @Test
    void relectureRateeGardeLesAnciennesCles() throws IOException {
        ecrire("2026-10.pem", TestKeys.privatePem(octobre));
        MutableClock horloge = new MutableClock(Instant.now());
        FileKeyResolver r = FileKeyResolver.ofDirectory(dir, null, horloge);
        assertEquals("2026-10", r.signingKey().kid());
        ecrire("2026-11.pem", "-----BEGIN PRIVATE KEY-----\ncassé\n-----END PRIVATE KEY-----\n");
        horloge.advance(Duration.ofMinutes(2));
        assertEquals("2026-10", r.signingKey().kid());
    }

    @Test
    void pemInvalides() {
        assertThrows(IllegalArgumentException.class, () -> PemKeys.readPrivateKey("pas une clé"));
        assertThrows(IllegalArgumentException.class, () -> PemKeys.readPrivateKey(TestKeys.publicPem(octobre)));
        assertThrows(IllegalArgumentException.class, () -> PemKeys.readPublicKey(TestKeys.privatePem(octobre)));
        assertEquals(octobre.getPublic(), PemKeys.readPublicKey(TestKeys.publicPem(octobre)));
    }

    @Test
    void laCleNApparaitJamaisDansToString() {
        SigningKey k = FileKeyResolver.ofPem(TestKeys.privatePem(octobre), "k1").signingKey();
        assertEquals("SigningKey[kid=k1]", k.toString());
    }
}
