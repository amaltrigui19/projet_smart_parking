package tn.supcom.smartparking.core.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An anomaly, as stored in the {@code evenements} collection (contract §2.14).
 *
 * @param id        API id ("evt_" + ObjectId); null before it is saved
 * @param spotIds   spots involved (empty for a forbidden zone)
 * @param zone      forbidden zone involved (only for a forbidden zone)
 * @param end       set when the anomaly is over; null otherwise
 * @param details   type-specific data (e.g. overstay: duration_min, max_duration_min); empty if none
 * @param handledBy API id of the operator ("u_…")
 */
public record Event(String id, String parkingId, EventType type, List<String> spotIds, String zone,
                    Instant start, Instant end, EventSource source, Map<String, Object> details,
                    boolean handled, String handledBy, Instant handledAt, String comment) {

    public Event {
        if (parkingId == null || parkingId.isBlank()) throw new IllegalArgumentException("event: missing parking");
        if (type == null) throw new IllegalArgumentException("event: missing type");
        if (start == null) throw new IllegalArgumentException("event: missing start");
        if (source == null) throw new IllegalArgumentException("event: missing source");
        spotIds = spotIds == null ? List.of() : List.copyOf(spotIds);
        if (type == EventType.FORBIDDEN_ZONE) {
            if (zone == null || zone.isBlank()) throw new IllegalArgumentException("forbidden-zone event: missing zone");
        } else if (spotIds.isEmpty()) {
            throw new IllegalArgumentException("event " + type.value() + ": at least one spot");
        }
        if (end != null && end.isBefore(start)) throw new IllegalArgumentException("event: end before start");
        // Map.copyOf rejects null values: unmodifiable copy that accepts them
        details = details == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    /** New anomaly, not handled yet. */
    public static Event create(String parkingId, EventType type, List<String> spotIds, String zone,
                               Instant start, EventSource source, Map<String, Object> details) {
        return new Event(null, parkingId, type, spotIds, zone, start, null, source, details, false, null, null, null);
    }

    public Event withId(String newId) {
        return new Event(newId, parkingId, type, spotIds, zone, start, end, source, details,
                handled, handledBy, handledAt, comment);
    }
}
