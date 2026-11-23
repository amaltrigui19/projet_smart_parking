package tn.supcom.smartparking.iam.core.token;

import java.time.Instant;
import tn.supcom.smartparking.iam.model.Role;

/** Revendications d'un jeton dont la signature, l'émetteur et l'expiration ont été vérifiés. */
public record TokenClaims(String kid, String subject, String email, Role role,
                          String jwtId, Instant issuedAt, Instant expiresAt) {
}
