package tn.supcom.smartparking.core.model;

import java.time.Instant;
import java.util.List;

/**
 * A parking spot, as stored in the {@code places} collection (contract §2.14).
 *
 * @param polygon   polygon in camera pixels (used for detection)
 * @param planRect  rectangle in cm on the floor plan (used by the SVG map); may be null until the plan is entered
 * @param graphNode node of the guidance graph; may be null for now
 * @param since     time of the last state change; null if the Pi has not reported yet
 */
public record Spot(String id, String parkingId, SpotType type, int maxDurationMin,
                   List<Point> polygon, PlanRect planRect, String graphNode,
                   SpotState state, Instant since) {

    public Spot {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("spot: missing id");
        if (parkingId == null || parkingId.isBlank()) throw new IllegalArgumentException("spot " + id + ": missing parking");
        if (type == null) throw new IllegalArgumentException("spot " + id + ": missing type");
        if (maxDurationMin <= 0) throw new IllegalArgumentException("spot " + id + ": duree_max_min must be > 0");
        if (polygon == null || polygon.size() < 3) throw new IllegalArgumentException("spot " + id + ": polygon needs at least 3 points");
        polygon = List.copyOf(polygon);
        if (state == null) state = SpotState.FREE;
    }
}
