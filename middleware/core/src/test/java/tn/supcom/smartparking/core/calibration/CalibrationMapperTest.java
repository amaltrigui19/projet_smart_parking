package tn.supcom.smartparking.core.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;
import tn.supcom.smartparking.core.model.ForbiddenZone;
import tn.supcom.smartparking.core.model.Parking;
import tn.supcom.smartparking.core.model.PlanRect;
import tn.supcom.smartparking.core.model.Point;
import tn.supcom.smartparking.core.model.Spot;
import tn.supcom.smartparking.core.model.SpotState;
import tn.supcom.smartparking.core.model.SpotType;

class CalibrationMapperTest {

    private static final List<String> PLAN = List.of("A1", "A2", "A3", "A4", "B1", "B2", "B3", "B4");

    private static final String FILE = """
        {"parking": "P1", "resolution": [800, 600],
         "spots": [
           {"id": "B4", "type": "electric", "max_duration_min": 60, "polygon": [[340,280],[430,280],[430,420]]},
           {"id": "A1", "type": "accessible", "max_duration_min": 120, "polygon": [[40,60],[130,60],[130,200],[40,200]]}],
         "forbidden_zones": [{"id": "allee1", "polygon": [[500,210],[639,210],[639,280],[500,280]]},
                              {"id": "sortie", "polygon": [[0,0],[10,0],[10,10]]}]}
        """;

    private static final Parking EXISTING = new Parking("P1", "Maquette SUP'COM", 640, 480, 60, 40,
            List.of(new ForbiddenZone("allee1", List.of(new Point(1, 1), new Point(2, 1), new Point(2, 2)),
                    new PlanRect(50, 13, 10, 14))), null);

    private static JsonObject file() {
        return Json.createReader(new StringReader(FILE)).readObject();
    }

    @Test
    void readsSpots() {
        List<Spot> spots = CalibrationMapper.spots(file(), "P1");
        assertEquals(2, spots.size());
        Spot a1 = spots.get(1);
        assertEquals("A1", a1.id());
        assertEquals("P1", a1.parkingId());
        assertEquals(SpotType.ACCESSIBLE, a1.type());
        assertEquals(120, a1.maxDurationMin());
        assertEquals(new Point(130, 200), a1.polygon().get(2));
        assertEquals(SpotState.FREE, a1.state()); // unused: replacePolygons keeps the stored state
        assertNull(a1.planRect());
    }

    @Test
    void updatesParkingWithoutLosingPlan() {
        Parking p = CalibrationMapper.parking(file(), EXISTING);
        assertEquals("Maquette SUP'COM", p.name());
        assertEquals(800, p.widthPx());
        assertEquals(600, p.heightPx());
        assertEquals(60, p.widthCm());
        assertEquals(2, p.forbiddenZones().size());
        ForbiddenZone aisle = p.forbiddenZones().get(0);
        assertEquals(new PlanRect(50, 13, 10, 14), aisle.planRect()); // plan rect_cm kept
        assertEquals(new Point(639, 210), aisle.polygon().get(1));    // polygon replaced
        assertNull(p.forbiddenZones().get(1).planRect());             // new zone: no rect_cm yet
    }

    @Test
    void roundTripInContractFormat() {
        Parking p = CalibrationMapper.parking(file(), EXISTING);
        JsonObject reread = CalibrationMapper.toFile(p, CalibrationMapper.spots(file(), "P1"));
        assertEquals("P1", reread.getString("parking"));
        assertEquals(800, reread.getJsonArray("resolution").getInt(0));
        assertEquals("A1", reread.getJsonArray("spots").getJsonObject(0).getString("id")); // sorted by id
        assertEquals("accessible", reread.getJsonArray("spots").getJsonObject(0).getString("type"));
        assertEquals(List.of("id", "type", "max_duration_min", "polygon"),
                List.copyOf(reread.getJsonArray("spots").getJsonObject(0).keySet()));
        assertEquals(List.of("id", "polygon"), List.copyOf(reread.getJsonArray("forbidden_zones").getJsonObject(0).keySet()));
        assertEquals(List.of(), CalibrationValidator.validate(
                Json.createReader(new StringReader(reread.toString().replace("800", "640").replace("600", "480"))).readValue(),
                "P1", PLAN)
                .stream().filter(e -> !e.contains("sortie")).toList());
    }
}
