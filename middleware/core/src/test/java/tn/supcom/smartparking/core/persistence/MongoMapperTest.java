package tn.supcom.smartparking.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import tn.supcom.smartparking.core.model.ForbiddenZone;
import tn.supcom.smartparking.core.model.Parking;
import tn.supcom.smartparking.core.model.PlanRect;
import tn.supcom.smartparking.core.model.Point;
import tn.supcom.smartparking.core.model.Spot;
import tn.supcom.smartparking.core.model.SpotState;
import tn.supcom.smartparking.core.model.SpotType;

class MongoMapperTest {

    private static final List<Point> SQUARE =
            List.of(new Point(40, 60), new Point(130, 60), new Point(130, 200), new Point(40, 200));

    private static Spot spotA3() {
        return new Spot("A3", "P1", SpotType.ACCESSIBLE, 120, SQUARE, new PlanRect(30, 0, 8, 13), "A3",
                SpotState.OCCUPIED, Instant.parse("2026-11-20T10:02:11Z"));
    }

    @Test
    void spotRoundTripWithoutLoss() {
        Spot s = spotA3();
        assertEquals(s, MongoMapper.toSpot(MongoMapper.toDocument(s)));
    }

    @Test
    void fieldsNamedAsInContract() {
        Document d = MongoMapper.toDocument(spotA3());
        assertEquals("A3", d.get("_id"));
        assertEquals("accessible", d.get("type"));
        assertEquals(120, d.get("duree_max_min"));
        assertEquals("occupied", d.get("etat"));
        assertEquals(List.of(40, 60), ((List<?>) d.get("poly")).get(0));
    }

    @Test
    void readsDecimalNumbersWrittenByMongosh() {
        Document d = new Document("_id", "A1").append("parking", "P1").append("type", "standard")
                .append("duree_max_min", 120.0)
                .append("poly", List.of(List.of(149.0, 0.0), List.of(235.0, 0.0), List.of(235.0, 156.0)));
        Spot s = MongoMapper.toSpot(d);
        assertEquals(120, s.maxDurationMin());
        assertEquals(new Point(235, 156), s.polygon().get(2));
        assertEquals(SpotState.FREE, s.state());   // no state in database -> free
        assertNull(s.planRect());
        assertNull(s.since());
    }

    @Test
    void rejectsUnknownType() {
        Document d = MongoMapper.toDocument(spotA3()).append("type", "camion");
        assertThrows(IllegalArgumentException.class, () -> MongoMapper.toSpot(d));
    }

    @Test
    void rejectsPolygonWithFewerThan3Points() {
        assertThrows(IllegalArgumentException.class, () -> new Spot("A1", "P1", SpotType.STANDARD, 120,
                List.of(new Point(0, 0), new Point(10, 0)), null, null, null, null));
    }

    @Test
    void parkingRoundTripWithoutLoss() {
        Parking p = new Parking("P1", "Maquette SUP'COM", 640, 480, 60, 40,
                List.of(new ForbiddenZone("allee1", SQUARE, new PlanRect(50, 13, 10, 14))),
                Instant.parse("2026-11-16T12:00:00Z"));
        Document d = MongoMapper.parkingFields(p).append("_id", "P1");
        assertEquals(p, MongoMapper.toParking(d));
    }
}
