package tn.supcom.smartparking.core.model;

import java.time.Instant;
import java.util.List;

/**
 * A parking lot, as stored in the {@code parkings} collection (contract §2.14):
 * identity, camera resolution, physical size and forbidden zones.
 */
public record Parking(String id, String name, int widthPx, int heightPx,
                      double widthCm, double heightCm,
                      List<ForbiddenZone> forbiddenZones, Instant updatedAt) {

    public Parking {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("parking: missing id");
        if (widthPx <= 0 || heightPx <= 0) throw new IllegalArgumentException("parking " + id + ": invalid resolution");
        forbiddenZones = forbiddenZones == null ? List.of() : List.copyOf(forbiddenZones);
    }
}
