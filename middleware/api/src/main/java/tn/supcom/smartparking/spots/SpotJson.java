package tn.supcom.smartparking.spots;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import tn.supcom.smartparking.core.model.ForbiddenZone;
import tn.supcom.smartparking.core.model.Parking;
import tn.supcom.smartparking.core.model.PlanRect;
import tn.supcom.smartparking.core.model.Spot;
import tn.supcom.smartparking.core.spots.ParkingLayout;
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

    /** GET /api/parking (contract §4.3). {@code graph} stays null until the guidance (sprint 7). */
    static JsonObject layout(ParkingLayout layout) {
        Parking p = layout.parking();
        JsonArrayBuilder spots = Json.createArrayBuilder();
        for (Spot s : layout.spots()) {
            JsonObjectBuilder b = Json.createObjectBuilder().add("id", s.id()).add("type", s.type().value());
            addRect(b, s.planRect());
            if (s.graphNode() == null) {
                b.addNull("node");
            } else {
                b.add("node", s.graphNode());
            }
            spots.add(b);
        }
        JsonArrayBuilder zones = Json.createArrayBuilder();
        for (ForbiddenZone z : p.forbiddenZones()) {
            JsonObjectBuilder b = Json.createObjectBuilder().add("id", z.id());
            addRect(b, z.planRect());
            zones.add(b);
        }
        JsonObjectBuilder b = Json.createObjectBuilder().add("parking", p.id());
        if (p.name() == null) {
            b.addNull("name");
        } else {
            b.add("name", p.name());
        }
        return b.add("dimensions_cm", Json.createArrayBuilder().add(p.widthCm()).add(p.heightCm()))
                .add("spots", spots)
                .add("forbidden_zones", zones)
                .addNull("graph")
                .build();
    }

    /** rect_cm = [x, y, width, height] in cm, or null while the plan is not entered. */
    private static void addRect(JsonObjectBuilder b, PlanRect r) {
        if (r == null) {
            b.addNull("rect_cm");
        } else {
            b.add("rect_cm", Json.createArrayBuilder().add(r.x()).add(r.y()).add(r.width()).add(r.height()));
        }
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
