package tn.supcom.smartparking.iam;

/**
 * Erreur prévue par le contrat (§2.1 et §2.5). Le module api la transforme en réponse HTTP
 * {@code {"erreur": code, "message": message}} avec le statut donné.
 */
public class ErreurIam extends RuntimeException {

    private final int statut;
    private final String code;

    public ErreurIam(int statut, String code, String message) {
        super(message);
        this.statut = statut;
        this.code = code;
    }

    public int statut() {
        return statut;
    }

    public String code() {
        return code;
    }

    public static ErreurIam requeteInvalide(String message) {
        return new ErreurIam(400, "requete_invalide", message);
    }

    public static ErreurIam identifiantsInvalides() {
        // Même message que l'email ou le mot de passe soit faux (contrat §2.5, US-01).
        return new ErreurIam(401, "identifiants_invalides", "Email ou mot de passe incorrect.");
    }

    public static ErreurIam compteDesactive() {
        return new ErreurIam(403, "compte_desactive", "Ce compte est désactivé.");
    }

    public static ErreurIam emailExistant() {
        return new ErreurIam(409, "email_existant", "Un compte existe déjà avec cet email.");
    }
}
