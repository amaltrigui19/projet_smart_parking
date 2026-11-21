package tn.supcom.smartparking.core.calibration;

import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Rules of the {@code 400 config_invalide} error of PUT /api/config/polygones (contract §2.10, format §5.2).
 * Same rules as frontend/outils/valider_places.py, the fake API and the calibration page
 * (js/validation-places.js): duplicate id, polygon with fewer than 3 points, outside the resolution or flat,
 * unknown type, max duration ≤ 0, spot missing from the floor plan, wrong parking id.
 * Error messages stay in French: they are shown as-is to the operator.
 */
public final class CalibrationValidator {

    private static final Set<String> TYPES = Set.of("normale", "pmr", "electrique");
    private static final Pattern VALID_ID = Pattern.compile("^[A-Za-z0-9_-]+$");

    private CalibrationValidator() {
    }

    /**
     * @param expectedParkingId the only parking of the project (configured, "P1" by default)
     * @param planSpotIds       spot ids of the floor plan
     * @return the list of errors; empty if the file is valid
     */
    public static List<String> validate(JsonValue root, String expectedParkingId, List<String> planSpotIds) {
        List<String> errors = new ArrayList<>();
        if (root == null || root.getValueType() != JsonValue.ValueType.OBJECT) {
            errors.add("le fichier doit être un objet JSON");
            return errors;
        }
        JsonObject file = root.asJsonObject();

        String parking = text(file.get("parking"));
        if (parking == null || parking.isEmpty()) {
            errors.add("'parking' doit être un texte non vide, par exemple \"" + expectedParkingId + "\"");
        } else if (!expectedParkingId.equals(parking)) {
            errors.add("'parking' doit être \"" + expectedParkingId + "\" (un seul parking dans le projet)");
        }

        long width = Long.MAX_VALUE;
        long height = Long.MAX_VALUE;
        JsonValue resolution = file.get("resolution");
        if (resolution instanceof JsonArray r && r.size() == 2 && isPositiveInteger(r.get(0)) && isPositiveInteger(r.get(1))) {
            width = ((JsonNumber) r.get(0)).longValue();
            height = ((JsonNumber) r.get(1)).longValue();
        } else {
            errors.add("'resolution' doit être [largeur, hauteur] en pixels");
        }

        Set<String> seenIds = new HashSet<>();
        JsonValue spots = file.get("places");
        if (spots instanceof JsonArray list) {
            for (int i = 0; i < list.size(); i++) {
                if (!(list.get(i) instanceof JsonObject spot)) {
                    errors.add("places[" + i + "] doit être un objet");
                    continue;
                }
                String label = checkId("places[" + i + "]", spot, seenIds, errors);
                String type = text(spot.get("type"));
                if (type == null || !TYPES.contains(type)) {
                    errors.add(label + " : 'type' doit être l'un de [electrique, normale, pmr]");
                }
                JsonValue duration = spot.get("duree_max_min");
                if (!(isInteger(duration) && ((JsonNumber) duration).longValue() >= 1)) {
                    errors.add(label + " : 'duree_max_min' doit être un entier ≥ 1");
                }
                String id = text(spot.get("id"));
                if (id != null && VALID_ID.matcher(id).matches() && !planSpotIds.contains(id)) {
                    errors.add(id + " : cette place n'existe pas dans le plan (" + String.join(", ", planSpotIds) + ")");
                }
                checkPolygon(label, spot.get("poly"), width, height, errors);
            }
        } else {
            errors.add("'places' doit être une liste");
        }

        JsonValue zones = file.get("zones_interdites");
        if (zones instanceof JsonArray list) {
            for (int i = 0; i < list.size(); i++) {
                if (!(list.get(i) instanceof JsonObject zone)) {
                    errors.add("zones_interdites[" + i + "] doit être un objet");
                    continue;
                }
                checkPolygon(checkId("zones_interdites[" + i + "]", zone, seenIds, errors), zone.get("poly"), width, height, errors);
            }
        } else {
            errors.add("'zones_interdites' doit être une liste");
        }
        return errors;
    }

    private static String checkId(String label, JsonObject element, Set<String> seenIds, List<String> errors) {
        String id = text(element.get("id"));
        if (id == null || !VALID_ID.matcher(id).matches()) {
            errors.add(label + " : 'id' manquant ou invalide");
            return label;
        }
        if (!seenIds.add(id)) {
            errors.add(id + " : identifiant en double");
        }
        return id;
    }

    private static void checkPolygon(String label, JsonValue value, long width, long height, List<String> errors) {
        if (!(value instanceof JsonArray polygon) || polygon.size() < 3) {
            errors.add(label + " : 'poly' doit être une liste d'au moins 3 points");
            return;
        }
        long[][] points = new long[polygon.size()][];
        for (int i = 0; i < polygon.size(); i++) {
            if (!(polygon.get(i) instanceof JsonArray p && p.size() == 2 && isInteger(p.get(0)) && isInteger(p.get(1)))) {
                errors.add(label + " : le point " + i + " doit être [x, y] en pixels entiers");
                return;
            }
            long x = ((JsonNumber) p.get(0)).longValue();
            long y = ((JsonNumber) p.get(1)).longValue();
            points[i] = new long[] {x, y};
            if (x < 0 || x >= width || y < 0 || y >= height) {
                errors.add(label + " : le point " + i + " [" + x + ", " + y + "] sort de l'image " + width + " × " + height);
            }
        }
        if (doubleArea(points) == 0) {
            errors.add(label + " : le polygone est plat (aire nulle)");
        }
    }

    /** Twice the polygon area (shoelace formula): zero when the polygon is flat. */
    static long doubleArea(long[][] p) {
        long sum = 0;
        for (int i = 0; i < p.length; i++) {
            long[] a = p[i];
            long[] b = p[(i + 1) % p.length];
            sum += a[0] * b[1] - b[0] * a[1];
        }
        return Math.abs(sum);
    }

    private static String text(JsonValue v) {
        return v instanceof JsonString s ? s.getString() : null;
    }

    private static boolean isInteger(JsonValue v) {
        return v instanceof JsonNumber n && n.isIntegral();
    }

    private static boolean isPositiveInteger(JsonValue v) {
        return isInteger(v) && ((JsonNumber) v).longValue() > 0;
    }
}
