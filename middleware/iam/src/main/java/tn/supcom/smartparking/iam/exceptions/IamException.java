package tn.supcom.smartparking.iam.exceptions;

/**
 * Erreur prévue par le contrat : statut HTTP, code court en anglais et message en français.
 * Transformée en réponse {@code {"error": code, "message": message}} par
 * {@code api.error.IamExceptionMapper}.
 */
public class IamException extends RuntimeException {

    private final int status;
    private final String code;

    public IamException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static IamException invalidRequest(String message) {
        return new IamException(400, "invalid_request", message);
    }

    /** Même message que l'email ou le mot de passe soit faux : on ne révèle pas si le compte existe. */
    public static IamException invalidCredentials() {
        return new IamException(401, "invalid_credentials", "Email ou mot de passe incorrect.");
    }

    public static IamException unauthenticated() {
        return new IamException(401, "unauthenticated", "JWT absent, invalide ou expiré : reconnectez-vous.");
    }

    public static IamException accountDisabled() {
        return new IamException(403, "account_disabled", "Ce compte est désactivé.");
    }

    public static IamException emailAlreadyExists() {
        return new IamException(409, "email_already_exists", "Un compte existe déjà avec cet email.");
    }
}
