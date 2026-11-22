package tn.supcom.smartparking.iam.core.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Argon2UtilityTest {

    // Empreintes produites par une AUTRE implémentation (argon2-cffi 25.1, bibliothèque C de référence) :
    // elles prouvent que notre format PHC et notre calcul sont compatibles avec le standard.
    static final String CFFI_ASCII =
            "$argon2id$v=19$m=19456,t=2,p=1$sgxgv1Idjh3+uiJRZybKqg$ly8mPEzNUHUz2NquBYCIbpDZdjuDvWHISeEtahNuLyw";
    static final String CFFI_UNICODE =
            "$argon2id$v=19$m=19456,t=2,p=1$Y3g8Hhz40eEkSYERVL96iQ$aUkFvlGaDpGXvxQcs7Oxno5zWJ7gzTSRlHAfNmH6JRo";
    static final String CFFI_AUTRES_PARAMETRES =
            "$argon2id$v=19$m=65536,t=3,p=4$sqA2iPIrGm42Ah6Tyo4M9A$ktjcEOTDowjJS7IyOwEkSBXzKTo6drjOzSPC+sxeo/g";

    final Argon2Utility argon2 = new Argon2Utility(Argon2Settings.owasp());

    @Test
    void formatPhcStandard() {
        String h = argon2.hash("Motdepasse123!".toCharArray());
        assertTrue(h.matches("^\\$argon2id\\$v=19\\$m=19456,t=2,p=1\\$[A-Za-z0-9+/]{22}\\$[A-Za-z0-9+/]{43}$"), h);
    }

    @Test
    void verifieSonPropreHachageEtRefuseUnAutreMotDePasse() {
        String h = argon2.hash("Motdepasse123!".toCharArray());
        assertTrue(argon2.verify("Motdepasse123!".toCharArray(), h));
        assertFalse(argon2.verify("Motdepasse123?".toCharArray(), h));
        assertFalse(argon2.verify("".toCharArray(), h));
    }

    @Test
    void selAleatoireDeuxEmpreintesDifferentes() {
        assertNotEquals(argon2.hash("Motdepasse123!".toCharArray()), argon2.hash("Motdepasse123!".toCharArray()));
    }

    @Test
    void compatibleAvecLImplementationDeReference() {
        assertTrue(argon2.verify("Motdepasse123!".toCharArray(), CFFI_ASCII));
        assertFalse(argon2.verify("motdepasse123!".toCharArray(), CFFI_ASCII));
        assertTrue(argon2.verify("Été-2026 ✓ مرحبا".toCharArray(), CFFI_UNICODE)); // UTF-8
        assertTrue(argon2.verify("Motdepasse123!".toCharArray(), CFFI_AUTRES_PARAMETRES)); // paramètres lus dans l'empreinte
    }

    @Test
    void needsRehashSiLesParametresChangent() {
        assertFalse(argon2.needsRehash(CFFI_ASCII));
        assertTrue(argon2.needsRehash(CFFI_AUTRES_PARAMETRES));
        Argon2Utility plusFort = new Argon2Utility(new Argon2Settings(65_536, 3, 1, 16, 32));
        assertTrue(plusFort.needsRehash(CFFI_ASCII));
        assertFalse(plusFort.needsRehash(plusFort.hash("x".toCharArray())));
        assertTrue(argon2.needsRehash("pbkdf2_sha256$600000$c2Vs$ZW1w")); // ancien format : à refaire
        assertTrue(argon2.needsRehash(null));
    }

    @Test
    void empreintesMalFormeesOuDangereusesRefusees() {
        char[] mdp = "Motdepasse123!".toCharArray();
        assertFalse(argon2.verify(mdp, null));
        assertFalse(argon2.verify(mdp, ""));
        assertFalse(argon2.verify(mdp, "pbkdf2_sha256$600000$c2Vs$ZW1w"));
        assertFalse(argon2.verify(mdp, CFFI_ASCII.replace("argon2id", "argon2i")));
        assertFalse(argon2.verify(mdp, CFFI_ASCII.replace("v=19", "v=16")));
        assertFalse(argon2.verify(mdp, CFFI_ASCII.replace("m=19456", "m=99999999"))); // mémoire énorme : refusée sans calcul
        assertFalse(argon2.verify(mdp, CFFI_ASCII.replace("t=2", "t=500")));
        assertFalse(argon2.verify(mdp, CFFI_ASCII.replace(",p=1", "")));
        assertFalse(argon2.verify(mdp, CFFI_ASCII.replace("m=19456", "m=abc")));
        assertFalse(argon2.verify(mdp, CFFI_ASCII + "$en-trop"));
        assertFalse(argon2.verify(mdp, CFFI_ASCII.substring(0, CFFI_ASCII.length() - 2) + "!!"));
        assertFalse(argon2.verify(null, CFFI_ASCII));
    }

    @Test
    void verificationFacticeFaitLeMemeCalcul() {
        long debut = System.nanoTime();
        argon2.verify("Motdepasse123!".toCharArray(), CFFI_ASCII);
        long vraie = System.nanoTime() - debut;
        argon2.verifyDummy("x".toCharArray()); // premier appel : prépare l'empreinte factice
        debut = System.nanoTime();
        argon2.verifyDummy("Motdepasse123!".toCharArray());
        long factice = System.nanoTime() - debut;
        // Même ordre de grandeur (même paramètres Argon2) : pas de réponse instantanée pour un email inconnu.
        assertTrue(factice > vraie / 4, "factice=" + factice + " vraie=" + vraie);
        argon2.verifyDummy(null);
    }

    @Test
    void parametresHorsBornesRefuses() {
        assertThrows(IllegalArgumentException.class, () -> new Argon2Settings(4, 2, 1, 16, 32));
        assertThrows(IllegalArgumentException.class, () -> new Argon2Settings(19_456, 0, 1, 16, 32));
        assertThrows(IllegalArgumentException.class, () -> new Argon2Settings(19_456, 2, 0, 16, 32));
        assertThrows(IllegalArgumentException.class, () -> new Argon2Settings(19_456, 2, 1, 4, 32));
    }

    @Test
    void secretsEffacesEtConvertisEnUtf8() {
        char[] mdp = "Été".toCharArray();
        byte[] octets = Secrets.utf8(mdp);
        assertArrayEquals("Été".getBytes(StandardCharsets.UTF_8), octets);
        Secrets.wipe(mdp);
        Secrets.wipe(octets);
        assertArrayEquals(new char[3], mdp);
        assertArrayEquals(new byte[octets.length], octets);
        Secrets.wipe((char[]) null);
        assertEquals(0, Secrets.utf8(new char[0]).length);
    }
}
