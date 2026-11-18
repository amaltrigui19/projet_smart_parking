package tn.supcom.smartparking.core.modele;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Une anomalie, telle que stockée dans la collection {@code evenements} (contrat §2.14).
 *
 * @param id        identifiant de l'API (« evt_ » + ObjectId) ; null avant l'enregistrement
 * @param places    places concernées (vide pour zone_interdite)
 * @param zone      zone interdite concernée (seulement pour zone_interdite)
 * @param fin       rempli quand l'anomalie est finie ; sinon null
 * @param details   informations propres au type (ex. ventouse : duree_min, duree_max_min) ; vide si aucune
 * @param traitePar identifiant de l'API de l'exploitant (« u_… »)
 */
public record Evenement(String id, String parking, TypeEvenement type, List<String> places, String zone,
                        Instant debut, Instant fin, SourceEvenement source, Map<String, Object> details,
                        boolean traite, String traitePar, Instant traiteLe, String commentaire) {

    public Evenement {
        if (parking == null || parking.isBlank()) throw new IllegalArgumentException("événement : parking manquant");
        if (type == null) throw new IllegalArgumentException("événement : type manquant");
        if (debut == null) throw new IllegalArgumentException("événement : debut manquant");
        if (source == null) throw new IllegalArgumentException("événement : source manquante");
        places = places == null ? List.of() : List.copyOf(places);
        if (type == TypeEvenement.ZONE_INTERDITE) {
            if (zone == null || zone.isBlank()) throw new IllegalArgumentException("événement zone_interdite : zone manquante");
        } else if (places.isEmpty()) {
            throw new IllegalArgumentException("événement " + type.valeur() + " : au moins une place");
        }
        if (fin != null && fin.isBefore(debut)) throw new IllegalArgumentException("événement : fin avant debut");
        // Map.copyOf refuse les valeurs null : copie non modifiable qui les accepte
        details = details == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    /** Nouvelle anomalie, non traitée. */
    public static Evenement nouveau(String parking, TypeEvenement type, List<String> places, String zone,
                                    Instant debut, SourceEvenement source, Map<String, Object> details) {
        return new Evenement(null, parking, type, places, zone, debut, null, source, details, false, null, null, null);
    }

    public Evenement avecId(String nouvelId) {
        return new Evenement(nouvelId, parking, type, places, zone, debut, fin, source, details,
                traite, traitePar, traiteLe, commentaire);
    }
}
