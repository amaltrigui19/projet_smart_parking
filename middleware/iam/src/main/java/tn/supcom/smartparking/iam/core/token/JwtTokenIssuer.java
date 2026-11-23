package tn.supcom.smartparking.iam.core.token;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tn.supcom.smartparking.iam.keyresolver.KeyResolver;
import tn.supcom.smartparking.iam.keyresolver.SigningKey;
import tn.supcom.smartparking.iam.model.User;

/**
 * Émet des JWT RS256 avec Nimbus JOSE+JWT. En-tête : {@code alg=RS256}, {@code typ=JWT}, {@code kid} de la clé active.
 * Revendications : {@code iss}, {@code sub} (u_…), {@code upn} (email), {@code role} (lu par la PWA),
 * {@code groups} (lu par MicroProfile JWT pour @RolesAllowed), {@code iat}, {@code exp}, {@code jti}.
 */
@ApplicationScoped
public class JwtTokenIssuer implements TokenIssuer {

    /** Émetteur par défaut ; l'API le vérifie (mp.jwt.verify.issuer). */
    public static final String DEFAULT_ISSUER = "smartparking";

    @Inject
    KeyResolver keys;

    @Inject
    @ConfigProperty(name = "JWT_ISSUER", defaultValue = DEFAULT_ISSUER)
    String issuer;

    @Inject
    @ConfigProperty(name = "JWT_LIFETIME_SECONDS", defaultValue = "3600")
    long lifetimeSeconds;

    Clock clock = Clock.systemUTC();

    public JwtTokenIssuer() {
        // Pour CDI
    }

    public JwtTokenIssuer(KeyResolver keys, String issuer, Duration lifetime, Clock clock) {
        this.keys = keys;
        this.issuer = issuer;
        this.lifetimeSeconds = lifetime.toSeconds();
        this.clock = clock;
    }

    @Override
    public IssuedToken issue(User user) {
        SigningKey key = keys.signingKey();
        Instant now = clock.instant();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT)
                .keyID(key.kid())
                .build();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(user.id())
                .claim("upn", user.email())
                .claim("role", user.role().name())
                .claim("groups", List.of(user.role().name()))
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(lifetimeSeconds)))
                .jwtID(UUID.randomUUID().toString())
                .build();
        SignedJWT jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(new RSASSASigner(key.privateKey()));
        } catch (JOSEException e) {
            throw new IllegalStateException("Signature du JWT impossible (kid=" + key.kid() + ")", e);
        }
        return new IssuedToken(jwt.serialize(), lifetimeSeconds);
    }
}
