package tn.supcom.smartparking.iam.core.security;

/**
 * Hachage des mots de passe. Les mots de passe sont passés en {@code char[]} pour pouvoir être effacés
 * de la mémoire après usage ({@link Secrets#wipe(char[])}) ; l'appelant reste responsable de cet effacement.
 */
public interface PasswordHasher {

    /** Empreinte à stocker, avec un sel aléatoire et les paramètres de calcul. */
    String hash(char[] password);

    /** Vrai si le mot de passe correspond à l'empreinte stockée. Comparaison en temps constant. */
    boolean verify(char[] password, String storedHash);

    /**
     * Même calcul qu'une vraie vérification, quand l'email est inconnu : la réponse prend alors le même temps,
     * et on ne peut pas deviner si un compte existe en mesurant le délai.
     */
    void verifyDummy(char[] password);

    /** Vrai si l'empreinte a été calculée avec d'autres paramètres que les paramètres actuels (à refaire à la connexion). */
    boolean needsRehash(String storedHash);
}
