package tn.supcom.smartparking.core.donnees;

import java.util.Optional;
import org.bson.types.ObjectId;

/**
 * Identifiants de l'API (contrat §2.1) : en base, un {@code ObjectId} ; dans l'API, un préfixe
 * suivi de l'ObjectId en hexadécimal (ex. « r_6543a1f0… »).
 */
public final class Identifiants {

    public static final String UTILISATEUR = "u_";
    public static final String RESERVATION = "r_";
    public static final String EVENEMENT = "evt_";
    public static final String AUTORISATION = "aut_";

    private Identifiants() {
    }

    public static String versApi(String prefixe, ObjectId id) {
        return id == null ? null : prefixe + id.toHexString();
    }

    /** L'ObjectId d'un identifiant de l'API ; vide s'il est null, sans le bon préfixe ou mal formé. */
    public static Optional<ObjectId> depuisApi(String prefixe, String id) {
        if (id == null || !id.startsWith(prefixe)) {
            return Optional.empty();
        }
        String hex = id.substring(prefixe.length());
        return ObjectId.isValid(hex) ? Optional.of(new ObjectId(hex)) : Optional.empty();
    }

    /** Comme {@link #depuisApi}, mais lève IllegalArgumentException : pour les références obligatoires. */
    static ObjectId exiger(String prefixe, String id) {
        return depuisApi(prefixe, id).orElseThrow(
                () -> new IllegalArgumentException("identifiant invalide (attendu " + prefixe + "…) : " + id));
    }

    /** Comme {@link #exiger}, mais null reste null (références facultatives). */
    static ObjectId facultatif(String prefixe, String id) {
        return id == null ? null : exiger(prefixe, id);
    }
}
