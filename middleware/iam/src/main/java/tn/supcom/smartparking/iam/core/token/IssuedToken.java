package tn.supcom.smartparking.iam.core.token;

/**
 * Jeton émis : la valeur à envoyer dans {@code Authorization: Bearer}, et sa durée de vie.
 *
 * @param accessToken le JWT compact (en-tête.charge.signature)
 * @param expiresIn   durée de vie en secondes (champ {@code expires_in} de la réponse de connexion)
 */
public record IssuedToken(String accessToken, long expiresIn) {

    /** N'affiche pas le jeton. */
    @Override
    public String toString() {
        return "IssuedToken[expiresIn=" + expiresIn + "]";
    }
}
