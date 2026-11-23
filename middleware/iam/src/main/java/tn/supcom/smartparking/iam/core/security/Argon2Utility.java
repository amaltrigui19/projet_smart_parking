package tn.supcom.smartparking.iam.core.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Hachage Argon2id (RFC 9106) avec Bouncy Castle, au format PHC standard, lisible par les autres implémentations
 * (argon2-cffi, libargon2…) :
 * <pre>$argon2id$v=19$m=19456,t=2,p=1$&lt;sel base64&gt;$&lt;empreinte base64&gt;</pre>
 * Les paramètres sont enregistrés avec l'empreinte : on peut les augmenter sans invalider les comptes existants
 * ({@link #needsRehash(String)} dit qu'il faut refaire l'empreinte à la prochaine connexion).
 *
 * <p>Paramètres (MicroProfile Config) : {@code ARGON2_MEMORY_KIB} (19456), {@code ARGON2_ITERATIONS} (2),
 * {@code ARGON2_PARALLELISM} (1), soit la recommandation OWASP.
 */
@ApplicationScoped
public class Argon2Utility implements PasswordHasher {

    static final String ALGORITHM = "argon2id";
    static final int VERSION = Argon2Parameters.ARGON2_VERSION_13; // 0x13 = 19
    private static final Base64.Encoder B64 = Base64.getEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getDecoder();
    private static final SecureRandom RANDOM = new SecureRandom();

    @Inject
    @ConfigProperty(name = "ARGON2_MEMORY_KIB", defaultValue = "19456")
    int memoryKib;

    @Inject
    @ConfigProperty(name = "ARGON2_ITERATIONS", defaultValue = "2")
    int iterations;

    @Inject
    @ConfigProperty(name = "ARGON2_PARALLELISM", defaultValue = "1")
    int parallelism;

    private volatile Argon2Settings settings;
    private volatile String dummyHash;

    public Argon2Utility() {
        // Pour CDI
    }

    public Argon2Utility(Argon2Settings settings) {
        this.settings = settings;
    }

    public Argon2Settings settings() {
        if (settings == null) {
            Argon2Settings d = Argon2Settings.owasp();
            settings = new Argon2Settings(memoryKib, iterations, parallelism, d.saltLength(), d.hashLength());
        }
        return settings;
    }

    @Override
    public String hash(char[] password) {
        Argon2Settings s = settings();
        byte[] salt = new byte[s.saltLength()];
        RANDOM.nextBytes(salt);
        byte[] hash = derive(password, salt, s.memoryKib(), s.iterations(), s.parallelism(), s.hashLength());
        try {
            return "$" + ALGORITHM + "$v=" + VERSION + "$m=" + s.memoryKib() + ",t=" + s.iterations() + ",p=" + s.parallelism()
                    + "$" + B64.encodeToString(salt) + "$" + B64.encodeToString(hash);
        } finally {
            Secrets.wipe(hash);
        }
    }

    @Override
    public boolean verify(char[] password, String storedHash) {
        Optional<Phc> phc = Phc.parse(storedHash);
        if (password == null || phc.isEmpty()) {
            return false;
        }
        Phc p = phc.get();
        byte[] computed = derive(password, p.salt(), p.memoryKib(), p.iterations(), p.parallelism(), p.hash().length);
        try {
            return MessageDigest.isEqual(p.hash(), computed); // temps constant
        } finally {
            Secrets.wipe(computed);
        }
    }

    @Override
    public void verifyDummy(char[] password) {
        if (dummyHash == null) {
            dummyHash = hash("mot-de-passe-factice".toCharArray());
        }
        verify(password == null ? new char[0] : password, dummyHash);
    }

    @Override
    public boolean needsRehash(String storedHash) {
        Argon2Settings s = settings();
        return Phc.parse(storedHash)
                .map(p -> p.memoryKib() != s.memoryKib() || p.iterations() != s.iterations()
                        || p.parallelism() != s.parallelism() || p.hash().length != s.hashLength())
                .orElse(true);
    }

    private static byte[] derive(char[] password, byte[] salt, int memoryKib, int iterations, int parallelism, int length) {
        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(VERSION)
                .withMemoryAsKB(memoryKib)
                .withIterations(iterations)
                .withParallelism(parallelism)
                .withSalt(salt)
                .build();
        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);
        byte[] secret = Secrets.utf8(password);
        byte[] out = new byte[length];
        try {
            generator.generateBytes(secret, out);
            return out;
        } finally {
            Secrets.wipe(secret);
        }
    }

    /** Empreinte PHC lue et vérifiée ; vide si le format, l'algorithme, la version ou les bornes ne conviennent pas. */
    record Phc(int memoryKib, int iterations, int parallelism, byte[] salt, byte[] hash) {

        static Optional<Phc> parse(String s) {
            if (s == null) {
                return Optional.empty();
            }
            String[] parts = s.split("\\$", -1);
            // ["", "argon2id", "v=19", "m=…,t=…,p=…", sel, empreinte]
            if (parts.length != 6 || !parts[0].isEmpty() || !ALGORITHM.equals(parts[1]) || !("v=" + VERSION).equals(parts[2])) {
                return Optional.empty();
            }
            try {
                Map<String, Integer> values = new HashMap<>();
                for (String kv : parts[3].split(",")) {
                    String[] pair = kv.split("=", 2);
                    if (pair.length != 2 || values.put(pair[0], Integer.parseInt(pair[1])) != null) {
                        return Optional.empty();
                    }
                }
                if (values.size() != 3 || !values.keySet().containsAll(java.util.List.of("m", "t", "p"))) {
                    return Optional.empty();
                }
                byte[] salt = B64D.decode(parts[4]);
                byte[] hash = B64D.decode(parts[5]);
                // Mêmes bornes qu'à l'écriture : une empreinte trafiquée ne peut pas bloquer le serveur.
                new Argon2Settings(values.get("m"), values.get("t"), values.get("p"), salt.length, hash.length);
                return Optional.of(new Phc(values.get("m"), values.get("t"), values.get("p"), salt, hash));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
    }
}
