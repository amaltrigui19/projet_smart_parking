package tn.supcom.smartparking.iam.core.token;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tn.supcom.smartparking.iam.exceptions.IamException;
import tn.supcom.smartparking.iam.keyresolver.KeyResolver;
import tn.supcom.smartparking.iam.model.Role;
import tn.supcom.smartparking.iam.model.User;

/**
 * Vérifie un JWT avec Nimbus JOSE+JWT : algorithme RS256 uniquement (ni {@code none}, ni HS256),
 * {@code kid} connu du {@link KeyResolver}, signature, émetteur, expiration (30 s de tolérance d'horloge),
 * {@code sub} et {@code role} valides. Toute erreur donne la même réponse 401 {@code unauthenticated}.
 */
@ApplicationScoped
public class JwtTokenValidator implements TokenValidator {

    static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    @Inject
    KeyResolver keys;

    @Inject
    @ConfigProperty(name = "JWT_ISSUER", defaultValue = JwtTokenIssuer.DEFAULT_ISSUER)
    String issuer;

    Clock clock = Clock.systemUTC();

    public JwtTokenValidator() {
        // Pour CDI
    }

    public JwtTokenValidator(KeyResolver keys, String issuer, Clock clock) {
        this.keys = keys;
        this.issuer = issuer;
        this.clock = clock;
    }

    @Override
    public TokenClaims validate(String token) {
        if (token == null || token.isBlank()) {
            throw IamException.unauthenticated();
        }
        try {
            SignedJWT jwt = SignedJWT.parse(token.trim());
            JWSHeader header = jwt.getHeader();
            if (!JWSAlgorithm.RS256.equals(header.getAlgorithm()) || header.getKeyID() == null) {
                throw IamException.unauthenticated();
            }
            RSAPublicKey publicKey = keys.resolve(header.getKeyID()).orElseThrow(IamException::unauthenticated);
            if (!jwt.verify(new RSASSAVerifier(publicKey))) {
                throw IamException.unauthenticated();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Instant now = clock.instant();
            Instant expiresAt = instant(claims.getExpirationTime()).orElseThrow(IamException::unauthenticated);
            Instant issuedAt = instant(claims.getIssueTime()).orElseThrow(IamException::unauthenticated);
            if (!issuer.equals(claims.getIssuer())
                    || !now.isBefore(expiresAt.plus(CLOCK_SKEW))
                    || issuedAt.isAfter(now.plus(CLOCK_SKEW))
                    || claims.getSubject() == null
                    || !claims.getSubject().startsWith(User.ID_PREFIX)) {
                throw IamException.unauthenticated();
            }
            Role role = Role.parse(claims.getStringClaim("role")).orElseThrow(IamException::unauthenticated);
            return new TokenClaims(header.getKeyID(), claims.getSubject(), claims.getStringClaim("upn"), role,
                    claims.getJWTID(), issuedAt, expiresAt);
        } catch (ParseException | JOSEException e) {
            throw IamException.unauthenticated();
        }
    }

    private static Optional<Instant> instant(Date date) {
        return Optional.ofNullable(date).map(Date::toInstant);
    }
}
