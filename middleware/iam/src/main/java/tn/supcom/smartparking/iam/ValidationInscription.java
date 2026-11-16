package tn.supcom.smartparking.iam;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Règles d'entrée de l'inscription (contrat §2.5 et US-01) : nom non vide, email valide,
 * mot de passe d'au moins 8 caractères. Chaque méthode renvoie le message d'erreur, ou null si c'est valide.
 */
public final class ValidationInscription {

    public static final int LONGUEUR_MIN_MOT_DE_PASSE = 8;
    public static final int LONGUEUR_MAX_MOT_DE_PASSE = 128;
    public static final int LONGUEUR_MAX_NOM = 100;
    public static final int LONGUEUR_MAX_EMAIL = 254;
    // Volontairement simple : un « @ », un domaine avec un point, pas d'espace.
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private ValidationInscription() {
    }

    /** Email en minuscules et sans espaces autour : c'est la forme stockée et comparée. */
    public static String normaliserEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public static String verifierNom(String nom) {
        if (nom == null || nom.isBlank()) {
            return "Le nom est obligatoire.";
        }
        if (nom.trim().length() > LONGUEUR_MAX_NOM) {
            return "Le nom ne doit pas dépasser " + LONGUEUR_MAX_NOM + " caractères.";
        }
        return null;
    }

    public static String verifierEmail(String email) {
        if (email == null || email.isBlank()) {
            return "L'email est obligatoire.";
        }
        String e = normaliserEmail(email);
        if (e.length() > LONGUEUR_MAX_EMAIL || !EMAIL.matcher(e).matches()) {
            return "L'email n'est pas valide.";
        }
        return null;
    }

    public static String verifierMotDePasse(String motDePasse) {
        if (motDePasse == null || motDePasse.length() < LONGUEUR_MIN_MOT_DE_PASSE) {
            return "Le mot de passe doit contenir au moins " + LONGUEUR_MIN_MOT_DE_PASSE + " caractères.";
        }
        if (motDePasse.length() > LONGUEUR_MAX_MOT_DE_PASSE) {
            return "Le mot de passe ne doit pas dépasser " + LONGUEUR_MAX_MOT_DE_PASSE + " caractères.";
        }
        return null;
    }

    /** Première erreur trouvée sur les trois champs, ou null si tout est valide. */
    public static String verifier(String nom, String email, String motDePasse) {
        String erreur = verifierNom(nom);
        if (erreur == null) {
            erreur = verifierEmail(email);
        }
        if (erreur == null) {
            erreur = verifierMotDePasse(motDePasse);
        }
        return erreur;
    }
}
