package tn.supcom.smartparking.iam.model;

import java.util.Optional;

/**
 * Rôles du contrat en anglais (§1). Le nom sert aussi de groupe MicroProfile JWT ({@code groups})
 * pour {@code @RolesAllowed}, et de valeur stockée dans MongoDB.
 */
public enum Role {
    DRIVER,
    OPERATOR;

    /** Rôle correspondant à ce texte (exactement le nom), ou vide. */
    public static Optional<Role> parse(String value) {
        for (Role r : values()) {
            if (r.name().equals(value)) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }
}
