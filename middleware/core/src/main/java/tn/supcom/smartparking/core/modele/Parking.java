package tn.supcom.smartparking.core.modele;

import java.time.Instant;
import java.util.List;

/**
 * Un parking, tel que stocké dans la collection {@code parkings} (contrat §2.14).
 * Sprint 3 : identité, résolution caméra, dimensions et zones interdites.
 * Le graphe, les paramètres et le statut du Pi sont ajoutés aux sprints suivants.
 */
public record Parking(String id, String nom, int largeurPx, int hauteurPx,
                      double largeurCm, double hauteurCm,
                      List<ZoneInterdite> zonesInterdites, Instant majLe) {

    public Parking {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("parking : id manquant");
        if (largeurPx <= 0 || hauteurPx <= 0) throw new IllegalArgumentException("parking " + id + " : resolution invalide");
        zonesInterdites = zonesInterdites == null ? List.of() : List.copyOf(zonesInterdites);
    }
}