package tn.supcom.smartparking.core.model;

import java.util.List;

/** Area where parking is not allowed (e.g. "allee1"), stored in the parking document. */
public record ForbiddenZone(String id, List<Point> polygon, PlanRect planRect) {
    public ForbiddenZone {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("zone: missing id");
        if (polygon == null || polygon.size() < 3) throw new IllegalArgumentException("zone " + id + ": polygon needs at least 3 points");
        polygon = List.copyOf(polygon);
    }
}
