package tn.supcom.smartparking.core.modele;

import java.time.Instant;

/**
 * Une autorisation PMR ou électrique, telle que stockée dans la collection {@code autorisations} (contrat §2.14).
 *
 * @param id          identifiant de l'API (« aut_ » + ObjectId) ; null avant l'enregistrement
 * @param utilisateur identifiant de l'API du conducteur (« u_… »)
 * @param fin         null = sans fin
 * @param accordeePar identifiant de l'API de l'exploitant (« u_… ») ; null pour les données d'exemple
 */
public record Autorisation(String id, String utilisateur, TypeAutorisation type,
                           Instant debut, Instant fin, String accordeePar) {

    public Autorisation {
        if (utilisateur == null || utilisateur.isBlank()) throw new IllegalArgumentException("autorisation : utilisateur manquant");
        if (type == null) throw new IllegalArgumentException("autorisation : type manquant");
        if (debut == null) throw new IllegalArgumentException("autorisation : debut manquant");
        if (fin != null && !fin.isAfter(debut)) throw new IllegalArgumentException("autorisation : fin doit être après debut");
    }

    /** Vrai si l'autorisation est en vigueur à l'instant donné (début inclus, fin exclue). */
    public boolean valideLe(Instant instant) {
        return !instant.isBefore(debut) && (fin == null || instant.isBefore(fin));
    }

    /** La même autorisation avec son identifiant, après l'enregistrement. */
    public Autorisation avecId(String nouvelId) {
        return new Autorisation(nouvelId, utilisateur, type, debut, fin, accordeePar);
    }
}
