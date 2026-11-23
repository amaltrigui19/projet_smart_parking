package tn.supcom.smartparking.core.persistence;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import tn.supcom.smartparking.core.model.ForbiddenZone;
import tn.supcom.smartparking.core.model.Parking;
import tn.supcom.smartparking.core.model.PlanRect;
import tn.supcom.smartparking.core.model.Point;
import tn.supcom.smartparking.core.model.Spot;
import tn.supcom.smartparking.core.model.SpotState;
import tn.supcom.smartparking.core.model.SpotType;

/**
 * Converts between Java models and MongoDB documents (contract §2.14). No database access.
 * Field names follow the English contract (snake_case).
 */
final class MongoMapper {

    private MongoMapper() {
    }

    // ---------- Spots ----------

    static Document toDocument(Spot s) {
        return new Document("_id", s.id())
                .append("parking", s.parkingId())
                .append("type", s.type().value())
                .append("max_duration_min", s.maxDurationMin())
                .append("polygon", polygonToList(s.polygon()))
                .append("rect_cm", rectToList(s.planRect()))
                .append("node", s.graphNode())
                .append("state", s.state().value())
                .append("since", s.since() == null ? null : Date.from(s.since()));
    }

    static Spot toSpot(Document d) {
        String state = d.getString("state");
        Date since = d.getDate("since");
        return new Spot(
                d.getString("_id"),
                d.getString("parking"),
                SpotType.fromValue(d.getString("type")),
                number(d.get("max_duration_min")).intValue(),
                listToPolygon(d.get("polygon")),
                listToRect(d.get("rect_cm")),
                d.getString("node"),
                state == null ? SpotState.FREE : SpotState.fromValue(state),
                since == null ? null : since.toInstant());
    }

    // ---------- Parkings ----------

    /** Parking fields WITHOUT _id, used in a $set (see MongoParkingRepository). */
    static Document parkingFields(Parking p) {
        List<Document> zones = new ArrayList<>();
        for (ForbiddenZone z : p.forbiddenZones()) {
            zones.add(new Document("id", z.id())
                    .append("polygon", polygonToList(z.polygon()))
                    .append("rect_cm", rectToList(z.planRect())));
        }
        return new Document("name", p.name())
                .append("resolution", List.of(p.widthPx(), p.heightPx()))
                .append("dimensions_cm", List.of(p.widthCm(), p.heightCm()))
                .append("forbidden_zones", zones)
                .append("updated_at", p.updatedAt() == null ? null : Date.from(p.updatedAt()));
    }

    static Parking toParking(Document d) {
        List<?> resolution = (List<?>) d.get("resolution");
        List<?> dimensions = (List<?>) d.get("dimensions_cm");
        List<ForbiddenZone> zones = new ArrayList<>();
        if (d.get("forbidden_zones") instanceof List<?> list) {
            for (Object o : list) {
                Document z = (Document) o;
                zones.add(new ForbiddenZone(z.getString("id"), listToPolygon(z.get("polygon")), listToRect(z.get("rect_cm"))));
            }
        }
        Date updatedAt = d.getDate("updated_at");
        return new Parking(
                d.getString("_id"),
                d.getString("name"),
                number(resolution.get(0)).intValue(),
                number(resolution.get(1)).intValue(),
                dimensions == null ? 0 : number(dimensions.get(0)).doubleValue(),
                dimensions == null ? 0 : number(dimensions.get(1)).doubleValue(),
                zones,
                updatedAt == null ? null : updatedAt.toInstant());
    }

    // ---------- Helpers ----------

    /** [[x, y], ...]: the "polygon" format of places.json (contract §5.2). */
    static List<List<Integer>> polygonToList(List<Point> polygon) {
        List<List<Integer>> list = new ArrayList<>();
        for (Point p : polygon) {
            list.add(List.of(p.x(), p.y()));
        }
        return list;
    }

    static List<Point> listToPolygon(Object value) {
        List<Point> polygon = new ArrayList<>();
        if (value instanceof List<?> points) {
            for (Object o : points) {
                List<?> xy = (List<?>) o;
                polygon.add(new Point(number(xy.get(0)).intValue(), number(xy.get(1)).intValue()));
            }
        }
        return polygon;
    }

    static List<Double> rectToList(PlanRect r) {
        return r == null ? null : List.of(r.x(), r.y(), r.width(), r.height());
    }

    static PlanRect listToRect(Object value) {
        if (!(value instanceof List<?> l) || l.size() != 4) {
            return null;
        }
        return new PlanRect(number(l.get(0)).doubleValue(), number(l.get(1)).doubleValue(),
                number(l.get(2)).doubleValue(), number(l.get(3)).doubleValue());
    }

    /** MongoDB may return 120 (Integer) or 120.0 (Double) depending on the writer: accept both. */
    private static Number number(Object o) {
        if (o instanceof Number n) {
            return n;
        }
        throw new IllegalStateException("number expected in database, got: " + o);
    }
}
