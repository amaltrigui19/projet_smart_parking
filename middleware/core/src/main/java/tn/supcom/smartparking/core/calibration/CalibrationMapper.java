package tn.supcom.smartparking.core.calibration;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import tn.supcom.smartparking.core.model.ForbiddenZone;
import tn.supcom.smartparking.core.model.Parking;
import tn.supcom.smartparking.core.model.PlanRect;
import tn.supcom.smartparking.core.model.Point;
import tn.supcom.smartparking.core.model.Spot;
import tn.supcom.smartparking.core.model.SpotType;

/**
 * Converts between the places.json calibration file (contract §5.2) and the domain models.
 * No database access. Only call it on a file already checked by {@link CalibrationValidator}.
 */
public final class CalibrationMapper {

    private CalibrationMapper() {
    }

    /** Spots of the file. Only id, type, max duration and polygon matter: state and plan data stay in the database. */
    public static List<Spot> spots(JsonObject file, String parkingId) {
        List<Spot> spots = new ArrayList<>();
        for (JsonValue v : file.getJsonArray("spots")) {
            JsonObject s = v.asJsonObject();
            spots.add(new Spot(s.getString("id"), parkingId, SpotType.fromValue(s.getString("type")),
                    s.getInt("max_duration_min"), points(s.getJsonArray("polygon")), null, null, null, null));
        }
        return spots;
    }

    /**
     * Forbidden zones of the file. The floor-plan rectangle (rect_cm) is not part of places.json:
     * the one already stored for the same zone is kept, if any.
     */
    public static List<ForbiddenZone> zones(JsonObject file, Parking existing) {
        List<ForbiddenZone> zones = new ArrayList<>();
        for (JsonValue v : file.getJsonArray("forbidden_zones")) {
            JsonObject z = v.asJsonObject();
            String id = z.getString("id");
            PlanRect rect = existing == null ? null : existing.forbiddenZones().stream()
                    .filter(e -> e.id().equals(id)).map(ForbiddenZone::planRect).findFirst().orElse(null);
            zones.add(new ForbiddenZone(id, points(z.getJsonArray("polygon")), rect));
        }
        return zones;
    }

    /** The updated parking: new resolution and zones; name and physical size unchanged. */
    public static Parking parking(JsonObject file, Parking existing) {
        JsonArray resolution = file.getJsonArray("resolution");
        return new Parking(existing.id(), existing.name(), resolution.getInt(0), resolution.getInt(1),
                existing.widthCm(), existing.heightCm(), zones(file, existing), null);
    }

    /** The places.json file (§5.2) rebuilt from the database, spots sorted by id. */
    public static JsonObject toFile(Parking parking, List<Spot> spots) {
        JsonArrayBuilder spotList = Json.createArrayBuilder();
        spots.stream().sorted(Comparator.comparing(Spot::id)).forEach(s -> spotList.add(Json.createObjectBuilder()
                .add("id", s.id())
                .add("type", s.type().value())
                .add("max_duration_min", s.maxDurationMin())
                .add("polygon", polygon(s.polygon()))));
        JsonArrayBuilder zoneList = Json.createArrayBuilder();
        parking.forbiddenZones().stream().sorted(Comparator.comparing(ForbiddenZone::id))
                .forEach(z -> zoneList.add(Json.createObjectBuilder().add("id", z.id()).add("polygon", polygon(z.polygon()))));
        return Json.createObjectBuilder()
                .add("parking", parking.id())
                .add("resolution", Json.createArrayBuilder().add(parking.widthPx()).add(parking.heightPx()))
                .add("spots", spotList)
                .add("forbidden_zones", zoneList)
                .build();
    }

    private static List<Point> points(JsonArray polygon) {
        List<Point> points = new ArrayList<>();
        for (JsonValue v : polygon) {
            JsonArray xy = v.asJsonArray();
            points.add(new Point(xy.getInt(0), xy.getInt(1)));
        }
        return points;
    }

    private static JsonArrayBuilder polygon(List<Point> polygon) {
        JsonArrayBuilder b = Json.createArrayBuilder();
        polygon.forEach(p -> b.add(Json.createArrayBuilder().add(p.x()).add(p.y())));
        return b;
    }
}
