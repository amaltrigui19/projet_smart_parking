package tn.supcom.smartparking.iam.core.validation;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Nom non vide (100 caractères au plus), email valide (254 au plus), mot de passe de 8 à 128 caractères. */
@ApplicationScoped
public class DefaultRegistrationValidator implements RegistrationValidator {

    static final int MAX_NAME_LENGTH = 100;
    static final int MAX_EMAIL_LENGTH = 254;
    // Volontairement simple : un « @ », un domaine avec un point, pas d'espace.
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    @Override
    public Optional<String> validate(String name, String email, char[] password) {
        if (name == null || name.isBlank()) {
            return Optional.of("Le nom est obligatoire.");
        }
        if (name.trim().length() > MAX_NAME_LENGTH) {
            return Optional.of("Le nom ne doit pas dépasser " + MAX_NAME_LENGTH + " caractères.");
        }
        if (email == null || email.isBlank()) {
            return Optional.of("L'email est obligatoire.");
        }
        String e = normalizeEmail(email);
        if (e.length() > MAX_EMAIL_LENGTH || !EMAIL.matcher(e).matches()) {
            return Optional.of("L'email n'est pas valide.");
        }
        if (password == null || password.length < MIN_PASSWORD_LENGTH) {
            return Optional.of("Le mot de passe doit contenir au moins " + MIN_PASSWORD_LENGTH + " caractères.");
        }
        if (password.length > MAX_PASSWORD_LENGTH) {
            return Optional.of("Le mot de passe ne doit pas dépasser " + MAX_PASSWORD_LENGTH + " caractères.");
        }
        return Optional.empty();
    }

    @Override
    public String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
