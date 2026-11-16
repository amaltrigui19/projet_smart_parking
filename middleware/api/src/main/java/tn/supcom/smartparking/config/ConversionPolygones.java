package tn.supcom.smartparking.config;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import tn.supcom.smartparking.core.modele.Parking;
import tn.supcom.smartparking.core.modele.Place;
import tn.supcom.smartparking.core.modele.Point;
import tn.supcom.smartparking.core.modele.RectCm;
import tn.supcom.smartparking.core.modele.TypePlace;
import tn.supcom.smartparking.core.modele.ZoneInterdite;

/**
 * Passage entre le fichier places.json (contrat §5.2) et les modèles de core (Parking, Place, ZoneInterdite).
 * Sans accès à la base. À n'appeler qu'après ValidationPolygones.valider (fichier déjà vérifié).
 */
public final class ConversionPolygones {

    private ConversionPolygones() {
    }

    /** Places du fichier. Seuls id, type, durée et polygone comptent : l'état et le plan restent en base. */
    public static List<Place> places(JsonObject fichier) {
        List<Place> places = new ArrayList<>();
        for (JsonValue v : fichier.getJsonArray("places")) {
            JsonObject p = v.asJsonObject();
            places.add(new Place(p.getString("id"), ValidationPolygones.PARKING,
                    TypePlace.depuis(p.getString("type")), p.getInt("duree_max_min"),
                    points(p.getJsonArray("poly")), null, null, null, null));
        }
        return places;
    }

    /**
     * Zones interdites du fichier. Le rectangle en cm du plan (rect_cm) n'est pas dans places.json :
     * on garde celui déjà enregistré pour la même zone, s'il existe.
     */
    public static List<ZoneInterdite> zones(JsonObject fichier, Parking existant) {
        List<ZoneInterdite> zones = new ArrayList<>();
        for (JsonValue v : fichier.getJsonArray("zones_interdites")) {
            JsonObject z = v.asJsonObject();
            String id = z.getString("id");
            RectCm rect = existant == null ? null : existant.zonesInterdites().stream()
                    .filter(e -> e.id().equals(id)).map(ZoneInterdite::rectCm).findFirst().orElse(null);
            zones.add(new ZoneInterdite(id, points(z.getJsonArray("poly")), rect));
        }
        return zones;
    }

    /** Le parking mis à jour : nouvelle résolution et nouvelles zones ; nom et dimensions inchangés. */
    public static Parking parking(JsonObject fichier, Parking existant) {
        JsonArray res = fichier.getJsonArray("resolution");
        return new Parking(existant.id(), existant.nom(), res.getInt(0), res.getInt(1),
                existant.largeurCm(), existant.hauteurCm(), zones(fichier, existant), null);
    }

    /** Le fichier places.json (§5.2) reconstruit depuis la base, places triées par identifiant. */
    public static JsonObject fichier(Parking parking, List<Place> places) {
        JsonArrayBuilder lp = Json.createArrayBuilder();
        places.stream().sorted(Comparator.comparing(Place::id)).forEach(p -> lp.add(Json.createObjectBuilder()
                .add("id", p.id())
                .add("type", p.type().valeur())
                .add("duree_max_min", p.dureeMaxMin())
                .add("poly", poly(p.poly()))));
        JsonArrayBuilder lz = Json.createArrayBuilder();
        parking.zonesInterdites().stream().sorted(Comparator.comparing(ZoneInterdite::id))
                .forEach(z -> lz.add(Json.createObjectBuilder().add("id", z.id()).add("poly", poly(z.poly()))));
        return Json.createObjectBuilder()
                .add("parking", parking.id())
                .add("resolution", Json.createArrayBuilder().add(parking.largeurPx()).add(parking.hauteurPx()))
                .add("places", lp)
                .add("zones_interdites", lz)
                .build();
    }

    private static List<Point> points(JsonArray poly) {
        List<Point> points = new ArrayList<>();
        for (JsonValue v : poly) {
            JsonArray xy = v.asJsonArray();
            points.add(new Point(xy.getInt(0), xy.getInt(1)));
        }
        return points;
    }

    private static JsonArrayBuilder poly(List<Point> poly) {
        JsonArrayBuilder b = Json.createArrayBuilder();
        poly.forEach(pt -> b.add(Json.createArrayBuilder().add(pt.x()).add(pt.y())));
        return b;
    }
}
