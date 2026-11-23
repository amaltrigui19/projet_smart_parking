package tn.supcom.smartparking.core.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;
import tn.supcom.smartparking.core.exception.InvalidConfigException;

/** Same cases as frontend/outils/test_validation.js and test_fausse_api.py (config_invalide rules). */
class CalibrationValidatorTest {

    private static final String PARKING = "P1";
    private static final List<String> PLAN = List.of("A1", "A2", "A3", "A4", "B1", "B2", "B3", "B4");

    private static final String VALID = """
        {"parking": "P1", "resolution": [640, 480],
         "spots": [
           {"id": "A1", "type": "accessible", "max_duration_min": 120, "polygon": [[40,60],[130,60],[130,200],[40,200]]},
           {"id": "B4", "type": "electric", "max_duration_min": 60, "polygon": [[340,280],[430,280],[430,420],[340,420]]}],
         "forbidden_zones": [{"id": "allee1", "polygon": [[500,210],[639,210],[639,280],[500,280]]}]}
        """;

    private static JsonValue json(String text) {
        return Json.createReader(new StringReader(text)).readValue();
    }

    private static List<String> validate(JsonValue value) {
        return CalibrationValidator.validate(value, PARKING, PLAN);
    }

    private static List<String> errors(String replace, String with) {
        assertTrue(VALID.contains(replace), "the test case changes nothing: " + replace);
        return validate(json(VALID.replace(replace, with)));
    }

    private static void contains(List<String> errors, String expected) {
        assertTrue(errors.stream().anyMatch(e -> e.contains(expected)), "expected « " + expected + " », got " + errors);
    }

    @Test
    void validFile() {
        assertEquals(List.of(), validate(json(VALID)));
    }

    @Test
    void duplicateId() {
        contains(errors("\"id\": \"B4\"", "\"id\": \"A1\""), "A1 : identifiant en double");
    }

    @Test
    void duplicateBetweenSpotAndZone() {
        contains(errors("\"id\": \"allee1\"", "\"id\": \"A1\""), "identifiant en double");
    }

    @Test
    void polygonWithTwoPoints() {
        contains(errors("[[40,60],[130,60],[130,200],[40,200]]", "[[40,60],[130,60]]"), "au moins 3 points");
    }

    @Test
    void pointOutsideResolution() {
        contains(errors("[130,60]", "[640,60]"), "sort de l'image 640 × 480");
    }

    @Test
    void nonIntegerPoint() {
        contains(errors("[130,60]", "[130.5,60]"), "pixels entiers");
    }

    @Test
    void flatPolygon() {
        contains(errors("[[40,60],[130,60],[130,200],[40,200]]", "[[1,1],[2,2],[3,3]]"), "plat");
    }

    @Test
    void unknownType() {
        contains(errors("\"type\": \"accessible\"", "\"type\": \"reserved\""), "'type' doit être");
    }

    @Test
    void zeroOrNegativeDuration() {
        contains(errors("\"max_duration_min\": 120", "\"max_duration_min\": 0"), "max_duration_min");
        contains(errors("\"max_duration_min\": 120", "\"max_duration_min\": -5"), "max_duration_min");
        contains(errors("\"max_duration_min\": 120", "\"max_duration_min\": 120.5"), "max_duration_min");
    }

    @Test
    void spotMissingFromPlan() {
        contains(errors("\"id\": \"A1\"", "\"id\": \"P9\""), "P9 : cette place n'existe pas dans le plan");
    }

    @Test
    void parkingOtherThanConfiguredOne() {
        contains(errors("\"parking\": \"P1\"", "\"parking\": \"P2\""), "'parking' doit être \"P1\"");
    }

    @Test
    void configuredParkingIdIsUsed() {
        assertEquals(List.of(), CalibrationValidator.validate(json(VALID.replace("\"P1\"", "\"P7\"")), "P7", PLAN));
    }

    @Test
    void missingOrInvalidResolution() {
        contains(errors("\"resolution\": [640, 480]", "\"resolution\": [640]"), "'resolution'");
        contains(errors("\"resolution\": [640, 480]", "\"resolution\": [0, 480]"), "'resolution'");
    }

    @Test
    void missingLists() {
        List<String> e = validate(json("{\"parking\": \"P1\", \"resolution\": [640, 480]}"));
        contains(e, "'spots' doit être une liste");
        contains(e, "'forbidden_zones' doit être une liste");
    }

    @Test
    void notAnObject() {
        contains(validate(json("[1, 2]")), "objet JSON");
    }

    @Test
    void severalErrorsInOneMessage() {
        String modified = VALID.replace("\"id\": \"B4\"", "\"id\": \"A1\"").replace("\"max_duration_min\": 120", "\"max_duration_min\": 0");
        List<String> e = validate(json(modified));
        assertEquals(2, e.size(), e.toString());
        String message = new InvalidConfigException(e).getMessage();
        assertEquals(2, message.split(" ; ").length); // the calibration page splits on « ; »
    }

    @Test
    void polygonArea() {
        assertEquals(2 * 90 * 140, CalibrationValidator.doubleArea(new long[][] {{40, 60}, {130, 60}, {130, 200}, {40, 200}}));
        assertEquals(0, CalibrationValidator.doubleArea(new long[][] {{1, 1}, {2, 2}, {3, 3}}));
    }

    @Test
    void emptyFile() {
        JsonObject empty = Json.createObjectBuilder().build();
        assertTrue(validate(empty).size() >= 4);
    }
}
