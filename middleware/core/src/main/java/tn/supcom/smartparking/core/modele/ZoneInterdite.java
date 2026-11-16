package tn.supcom.smartparking.core.modele;

import java.util.List;

/** Zone où l'on ne doit pas stationner (ex. « allee1 »), stockée dans le document du parking. */
public record ZoneInterdite(String id, List<Point> poly, RectCm rectCm) {
    public ZoneInterdite {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("zone : id manquant");
        if (poly == null || poly.size() < 3) throw new IllegalArgumentException("zone " + id + " : poly doit avoir au moins 3 points");
        poly = List.copyOf(poly);
    }
}