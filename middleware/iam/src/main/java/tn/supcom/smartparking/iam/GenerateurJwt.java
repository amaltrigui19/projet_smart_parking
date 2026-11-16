package tn.supcom.smartparking.iam;

import jakarta.json.Json;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/**
 * Émet les JWT signés en RS256 (contrat §2.2), avec la bibliothèque standard Java uniquement.
 *
 * <p>Revendications : {@code iss}, {@code sub} (identifiant u_…), {@code upn} (email), {@code role}
 * (lu par la PWA), {@code groups} (lu par WildFly pour @RolesAllowed), {@code iat}, {@code exp}, {@code jti}.
 * La vérification est faite par MicroProfile JWT dans WildFly, avec la clé publique.
 */
public final class GenerateurJwt {

    /** Émetteur attendu par WildFly (mp.jwt.verify.issuer). */
    public static final String EMETTEUR = "smartparking";
    /** Durée de vie du jeton : 1 heure (contrat §2.2). */
    public static final Duration DUREE_VIE = Duration.ofHours(1);

    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();
    private static final String ENTETE = B64URL.encodeToString(
            "{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private final PrivateKey clePrivee;
    private final String emetteur;
    private final Duration dureeVie;
    private final Clock horloge;

    public GenerateurJwt(PrivateKey clePrivee, String emetteur, Duration dureeVie, Clock horloge) {
        this.clePrivee = clePrivee;
        this.emetteur = emetteur;
        this.dureeVie = dureeVie;
        this.horloge = horloge;
    }

    public GenerateurJwt(PrivateKey clePrivee) {
        this(clePrivee, EMETTEUR, DUREE_VIE, Clock.systemUTC());
    }

    /** Durée de vie en secondes (champ expire_dans de la réponse de connexion). */
    public long dureeVieSecondes() {
        return dureeVie.toSeconds();
    }

    /** Jeton signé pour cet utilisateur. */
    public String emettre(Utilisateur u) {
        long maintenant = horloge.instant().getEpochSecond();
        String charge = Json.createObjectBuilder()
                .add("iss", emetteur)
                .add("sub", u.id())
                .add("upn", u.email())
                .add("role", u.role().name())
                .add("groups", Json.createArrayBuilder().add(u.role().name()))
                .add("iat", maintenant)
                .add("exp", maintenant + dureeVie.toSeconds())
                .add("jti", UUID.randomUUID().toString())
                .build()
                .toString();
        String aSigner = ENTETE + "." + B64URL.encodeToString(charge.getBytes(StandardCharsets.UTF_8));
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(clePrivee);
            signature.update(aSigner.getBytes(StandardCharsets.US_ASCII));
            return aSigner + "." + B64URL.encodeToString(signature.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Signature du JWT impossible", e);
        }
    }

    /**
     * Lit une clé privée RSA au format PEM PKCS#8 (« -----BEGIN PRIVATE KEY----- »),
     * celui produit par server/scripts/generer-cles-jwt.sh.
     */
    public static PrivateKey lireClePrivee(String pem) {
        if (pem == null || !pem.contains("-----BEGIN PRIVATE KEY-----")) {
            throw new IllegalArgumentException(
                    "Clé privée attendue au format PEM PKCS#8 (-----BEGIN PRIVATE KEY-----)");
        }
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new IllegalArgumentException("Clé privée RSA illisible", e);
        }
    }
}
