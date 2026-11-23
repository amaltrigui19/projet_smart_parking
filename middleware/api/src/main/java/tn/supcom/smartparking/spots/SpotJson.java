package tn.supcom.smartparking.spots;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import tn.supcom.smartparking.core.spots.SpotStateChanged;
import tn.supcom.smartparking.core.spots.SpotView;
import tn.supcom.smartparking.core.spots.SpotsSummary;

/** JSON of the spots routes (contract §2.6) and of the WebSocket messages (contract §1.4). */
final class SpotJson {

    private SpotJson() {
    }

    /** GET /api/spots. */
    static JsonObject list(String parkingId, List<SpotView> spots, Instant now) {
        SpotsSummary s = SpotsSummary.of(spots);
        JsonArrayBuilder array = Json.createArrayBuilder();
        spots.forEach(v -> array.add(spot(v)));
        return Json.createObjectBuilder()
                .add("parking", parkingId)
                .add("ts", date(now))
                .add("summary", Json.createObjectBuilder()
                        .add("total", s.total()).add("free", s.free())
                        .add("occupied", s.occupied()).add("reserved", s.reserved()))
                .add("spots", array)
                .build();
    }

    /** One spot of the list: id, type, state, since, max_duration_min. */
    static JsonObjectBuilder spot(SpotView v) {
        JsonObjectBuilder b = Json.createObjectBuilder()
                .add("id", v.id())
                .add("type", v.type().value())
                .add("state", v.state().value());
        addDate(b, "since", v.since());
        return b.add("max_duration_min", v.maxDurationMin());
    }

    /** GET /api/spots/{id}: the same fields, plus overdue; for the operator, lock (guidance, sprint 7). */
    static JsonObject detail(SpotView v, boolean operator) {
        JsonObjectBuilder b = spot(v).add("overdue", v.overdue());
        if (operator) {
            b.addNull("lock");
        }
        return b.build();
    }

    /** WebSocket message: {"spot": "A3", "state": "occupied", "since": "…"} (contract §1.4). */
    static String change(SpotStateChanged e) {
        JsonObjectBuilder b = Json.createObjectBuilder()
                .add("spot", e.spotId())
                .add("state", e.state().value());
        addDate(b, "since", e.since());
        return b.build().toString();
    }

    private static void addDate(JsonObjectBuilder b, String name, Instant value) {
        if (value == null) {
            b.addNull(name);
        } else {
            b.add(name, date(value));
        }
    }

    /** ISO 8601 UTC with Z, to the second (contract §1.1). */
    static String date(Instant i) {
        return i.truncatedTo(ChronoUnit.SECONDS).toString();
    }
}
