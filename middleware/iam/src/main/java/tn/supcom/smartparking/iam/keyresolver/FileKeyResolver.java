package tn.supcom.smartparking.iam.keyresolver;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Clés RSA lues sur le disque ou dans une variable d'environnement (MicroProfile Config), par ordre de priorité :
 * <ol>
 *   <li>{@code JWT_PRIVATE_KEY} : une clé privée PEM (une seule clé) ;</li>
 *   <li>{@code JWT_PRIVATE_KEY_FILE} : le chemin d'une clé privée PEM (une seule clé) ;</li>
 *   <li>{@code JWT_KEYS_DIR} (par défaut {@code /opt/smartparking/jwt/keys}) : un dossier de clés, pour la rotation.</li>
 * </ol>
 * Avec une seule clé, son {@code kid} vaut {@code JWT_KEY_ID}, ou à défaut l'empreinte RFC 7638 de la clé publique.
 *
 * <p>Dans le dossier, chaque fichier {@code <kid>.pem} est une clé : une clé <b>privée</b> peut signer et est publiée ;
 * une clé <b>publique</b> seule est publiée sans signer (ancienne clé, gardée le temps que ses jetons expirent).
 * La clé active est {@code JWT_ACTIVE_KID}, ou à défaut la clé privée dont le nom vient en dernier dans l'ordre
 * alphabétique (par exemple {@code 2026-12.pem} après {@code 2026-11.pem}). Le dossier est relu toutes les
 * 60 secondes : une rotation ne demande pas de redémarrage.
 *
 * <p>Les clés ne sont jamais écrites dans les journaux (seulement leur {@code kid}).
 */
@ApplicationScoped
public class FileKeyResolver implements KeyResolver {

    private static final Logger LOG = Logger.getLogger(FileKeyResolver.class.getName());
    static final Pattern KID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");
    static final Duration RELOAD_INTERVAL = Duration.ofSeconds(60);

    @Inject
    @ConfigProperty(name = "JWT_PRIVATE_KEY")
    Optional<String> privateKeyPem;

    @Inject
    @ConfigProperty(name = "JWT_PRIVATE_KEY_FILE")
    Optional<String> privateKeyFile;

    @Inject
    @ConfigProperty(name = "JWT_KEYS_DIR", defaultValue = "/opt/smartparking/jwt/keys")
    String keysDir;

    @Inject
    @ConfigProperty(name = "JWT_ACTIVE_KID")
    Optional<String> activeKid;

    @Inject
    @ConfigProperty(name = "JWT_KEY_ID")
    Optional<String> keyId;

    Clock clock = Clock.systemUTC();

    private volatile KeySet keys;
    private volatile Instant loadedAt;

    /** Clés en mémoire à un instant donné : la clé de signature et toutes les clés publiées. */
    record KeySet(SigningKey signing, Map<String, RSAPublicKey> publics, boolean fromDirectory) {
    }

    public FileKeyResolver() {
        // Pour CDI
    }

    /** Une seule clé, donnée en PEM (tests, ou JWT_PRIVATE_KEY). */
    public static FileKeyResolver ofPem(String pem, String kid) {
        FileKeyResolver r = new FileKeyResolver();
        r.privateKeyPem = Optional.of(pem);
        r.privateKeyFile = Optional.empty();
        r.activeKid = Optional.empty();
        r.keyId = Optional.ofNullable(kid);
        return r;
    }

    /** Un dossier de clés (tests, ou JWT_KEYS_DIR). */
    public static FileKeyResolver ofDirectory(Path dir, String activeKid, Clock clock) {
        FileKeyResolver r = new FileKeyResolver();
        r.privateKeyPem = Optional.empty();
        r.privateKeyFile = Optional.empty();
        r.keysDir = dir.toString();
        r.activeKid = Optional.ofNullable(activeKid);
        r.keyId = Optional.empty();
        r.clock = clock;
        return r;
    }

    @Override
    public Optional<RSAPublicKey> resolve(String kid) {
        if (kid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(current().publics().get(kid));
    }

    @Override
    public SigningKey signingKey() {
        return current().signing();
    }

    @Override
    public Map<String, RSAPublicKey> publicKeys() {
        return current().publics();
    }

    private KeySet current() {
        KeySet k = keys;
        Instant now = clock.instant();
        if (k == null) {
            synchronized (this) {
                if (keys == null) {
                    keys = load();
                    loadedAt = now;
                }
                return keys;
            }
        }
        if (k.fromDirectory() && Duration.between(loadedAt, now).compareTo(RELOAD_INTERVAL) >= 0) {
            synchronized (this) {
                if (Duration.between(loadedAt, now).compareTo(RELOAD_INTERVAL) >= 0) {
                    try {
                        keys = load();
                    } catch (RuntimeException e) {
                        // Fichier en cours d'écriture, par exemple : on garde les clés précédentes.
                        LOG.warning(() -> "Clés JWT non relues, anciennes clés gardées : " + e.getMessage());
                    }
                    loadedAt = now;
                }
            }
        }
        return keys;
    }

    KeySet load() {
        if (privateKeyPem.filter(s -> !s.isBlank()).isPresent()) {
            return single(privateKeyPem.get(), "JWT_PRIVATE_KEY");
        }
        if (privateKeyFile.filter(s -> !s.isBlank()).isPresent()) {
            return single(read(Path.of(privateKeyFile.get())), "JWT_PRIVATE_KEY_FILE");
        }
        return directory(Path.of(keysDir));
    }

    private KeySet single(String pem, String source) {
        RSAPrivateKey privateKey = PemKeys.readPrivateKey(pem);
        RSAPublicKey publicKey = PemKeys.publicKeyOf(privateKey);
        String kid = keyId.filter(s -> !s.isBlank()).orElseGet(() -> thumbprint(publicKey));
        checkKid(kid, source);
        LOG.info(() -> "Clé JWT chargée depuis " + source + ", kid=" + kid);
        return new KeySet(new SigningKey(kid, privateKey), Map.of(kid, publicKey), false);
    }

    private KeySet directory(Path dir) {
        if (!Files.isDirectory(dir)) {
            throw new IllegalStateException("Aucune clé JWT : définir JWT_PRIVATE_KEY, JWT_PRIVATE_KEY_FILE "
                    + "ou JWT_KEYS_DIR (dossier introuvable : " + dir + "). Voir middleware/iam/README.md.");
        }
        TreeMap<String, RSAPrivateKey> privates = new TreeMap<>();
        TreeMap<String, RSAPublicKey> publics = new TreeMap<>();
        List<String> ignored = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter(p -> p.getFileName().toString().endsWith(".pem")).sorted().toList()) {
                String name = f.getFileName().toString();
                String kid = name.substring(0, name.length() - ".pem".length());
                checkKid(kid, f.toString());
                String pem = read(f);
                if (PemKeys.isPrivateKey(pem)) {
                    RSAPrivateKey privateKey = PemKeys.readPrivateKey(pem);
                    privates.put(kid, privateKey);
                    publics.put(kid, PemKeys.publicKeyOf(privateKey));
                } else if (PemKeys.isPublicKey(pem)) {
                    publics.put(kid, PemKeys.readPublicKey(pem));
                } else {
                    ignored.add(name);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Dossier des clés JWT illisible : " + dir, e);
        }
        if (privates.isEmpty()) {
            throw new IllegalStateException("Aucune clé privée (<kid>.pem, -----BEGIN PRIVATE KEY-----) dans " + dir);
        }
        String active = activeKid.filter(s -> !s.isBlank()).orElse(privates.lastKey());
        if (!privates.containsKey(active)) {
            throw new IllegalStateException("JWT_ACTIVE_KID=" + active + " : aucune clé privée " + active + ".pem dans " + dir);
        }
        if (!ignored.isEmpty()) {
            LOG.warning(() -> "Fichiers .pem ignorés (ni clé privée PKCS#8, ni clé publique) : " + ignored);
        }
        LOG.info(() -> "Clés JWT chargées depuis " + dir + " : publiées=" + publics.keySet() + ", active=" + active);
        return new KeySet(new SigningKey(active, privates.get(active)),
                Collections.unmodifiableMap(new LinkedHashMap<>(publics)), true);
    }

    private static void checkKid(String kid, String source) {
        if (!KID.matcher(kid).matches()) {
            throw new IllegalStateException("kid invalide « " + kid + " » (" + source + ") : lettres, chiffres, . _ - ; 64 caractères au plus");
        }
    }

    private static String read(Path f) {
        try {
            return Files.readString(f, StandardCharsets.US_ASCII);
        } catch (IOException e) {
            throw new IllegalStateException("Clé JWT illisible : " + f, e);
        }
    }

    static String thumbprint(RSAPublicKey publicKey) {
        try {
            return new RSAKey.Builder(publicKey).build().computeThumbprint().toString();
        } catch (JOSEException e) {
            throw new IllegalStateException("Empreinte de la clé impossible à calculer", e);
        }
    }
}
