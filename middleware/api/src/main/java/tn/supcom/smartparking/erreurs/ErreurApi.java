package tn.supcom.smartparking.erreurs;

/**
 * Erreur prévue par le contrat, levée par le module api (remplace l'ancienne ErreurIam, qui n'existe plus :
 * l'IAM a ses propres erreurs et son propre mapper). Transformée en réponse par {@link MapperErreurApi}.
 */
public class ErreurApi extends RuntimeException {

    private final int statut;
    private final String code;

    public ErreurApi(int statut, String code, String message) {
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

    public static ErreurApi requeteInvalide(String message) {
        return new ErreurApi(400, "requete_invalide", message);
    }
}
