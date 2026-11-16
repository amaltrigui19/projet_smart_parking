package tn.supcom.smartparking.iam;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Hachage des mots de passe avec PBKDF2-HMAC-SHA256 (bibliothèque standard Java).
 *
 * <p>Format stocké : {@code pbkdf2_sha256$<itérations>$<sel base64>$<empreinte base64>}.
 * Le nombre d'itérations est enregistré avec l'empreinte : on pourra l'augmenter plus tard
 * sans invalider les comptes existants.
 */
public final class HachageMotDePasse {

    /** Recommandation OWASP pour PBKDF2-HMAC-SHA256. */
    public static final int ITERATIONS = 600_000;
    private static final String ALGORITHME = "PBKDF2WithHmacSHA256";
    private static final String PREFIXE = "pbkdf2_sha256";
    private static final int OCTETS_SEL = 16;
    private static final int BITS_EMPREINTE = 256;
    private static final SecureRandom ALEA = new SecureRandom();

    // Empreinte de référence, utilisée quand l'email n'existe pas (voir verifierFactice).
    private static final String EMPREINTE_FACTICE = hacher("mot-de-passe-factice".toCharArray());

    private HachageMotDePasse() {
    }

    /** Calcule l'empreinte d'un mot de passe, avec un sel aléatoire. */
    public static String hacher(char[] motDePasse) {
        byte[] sel = new byte[OCTETS_SEL];
        ALEA.nextBytes(sel);
        byte[] empreinte = deriver(motDePasse, sel, ITERATIONS);
        Base64.Encoder b64 = Base64.getEncoder();
        return PREFIXE + "$" + ITERATIONS + "$" + b64.encodeToString(sel) + "$" + b64.encodeToString(empreinte);
    }

    /** Vrai si le mot de passe correspond à l'empreinte stockée. Comparaison en temps constant. */
    public static boolean verifier(char[] motDePasse, String stocke) {
        if (stocke == null) {
            return false;
        }
        String[] parties = stocke.split("\\$");
        if (parties.length != 4 || !PREFIXE.equals(parties[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parties[1]);
            byte[] sel = Base64.getDecoder().decode(parties[2]);
            byte[] attendue = Base64.getDecoder().decode(parties[3]);
            byte[] calculee = deriver(motDePasse, sel, iterations);
            return MessageDigest.isEqual(attendue, calculee);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Fait le même calcul qu'une vraie vérification, quand l'email est inconnu : la réponse prend
     * alors le même temps, et on ne peut pas deviner si un email existe en mesurant le délai.
     */
    public static void verifierFactice(char[] motDePasse) {
        verifier(motDePasse, EMPREINTE_FACTICE);
    }

    private static byte[] deriver(char[] motDePasse, byte[] sel, int iterations) {
        KeySpec spec = new PBEKeySpec(motDePasse, sel, iterations, BITS_EMPREINTE);
        try {
            return SecretKeyFactory.getInstance(ALGORITHME).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 indisponible dans cette JVM", e);
        } finally {
            ((PBEKeySpec) spec).clearPassword();
        }
    }

    /** Efface un mot de passe de la mémoire dès qu'il n'est plus utile. */
    public static void effacer(char[] motDePasse) {
        if (motDePasse != null) {
            Arrays.fill(motDePasse, '\0');
        }
    }
}
