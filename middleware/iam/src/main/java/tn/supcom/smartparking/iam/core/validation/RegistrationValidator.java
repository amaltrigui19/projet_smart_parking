package tn.supcom.smartparking.iam.core.validation;

import java.util.Optional;

/** Règles d'entrée de l'inscription (nom, email, mot de passe). */
public interface RegistrationValidator {

    int MIN_PASSWORD_LENGTH = 8;
    int MAX_PASSWORD_LENGTH = 128;

    /** Premier problème trouvé (message en français, affiché par la PWA), ou vide si tout est valide. */
    Optional<String> validate(String name, String email, char[] password);

    /** Forme stockée et comparée de l'email (minuscules, sans espaces autour). */
    String normalizeEmail(String email);
}
