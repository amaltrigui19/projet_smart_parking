package tn.supcom.smartparking.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;
import tn.supcom.smartparking.core.modele.EtatPlace;
import tn.supcom.smartparking.core.modele.Parking;
import tn.supcom.smartparking.core.modele.Place;
import tn.supcom.smartparking.core.modele.Point;
import tn.supcom.smartparking.core.modele.RectCm;
import tn.supcom.smartparking.core.modele.TypePlace;
import tn.supcom.smartparking.core.modele.ZoneInterdite;

class ConversionPolygonesTest {

    private static final String FICHIER = """
        {"parking": "P1", "resolution": [800, 600],
         "places": [
           {"id": "B4", "type": "electrique", "duree_max_min": 60, "poly": [[340,280],[430,280],[430,420]]},
           {"id": "A1", "type": "pmr", "duree_max_min": 120, "poly": [[40,60],[130,60],[130,200],[40,200]]}],
         "zones_interdites": [{"id": "allee1", "poly": [[500,210],[639,210],[639,280],[500,280]]},
                              {"id": "sortie", "poly": [[0,0],[10,0],[10,10]]}]}
        """;

    private static final Parking EXISTANT = new Parking("P1", "Maquette SUP'COM", 640, 480, 60, 40,
            List.of(new ZoneInterdite("allee1", List.of(new Point(1, 1), new Point(2, 1), new Point(2, 2)),
                    new RectCm(50, 13, 10, 14))), null);

    private static JsonObject fichier() {
        return Json.createReader(new StringReader(FICHIER)).readObject();
    }

    @Test
    void placesLues() {
        List<Place> places = ConversionPolygones.places(fichier());
        assertEquals(2, places.size());
        Place a1 = places.get(1);
        assertEquals("A1", a1.id());
        assertEquals("P1", a1.parking());
        assertEquals(TypePlace.PMR, a1.type());
        assertEquals(120, a1.dureeMaxMin());
        assertEquals(new Point(130, 200), a1.poly().get(2));
        assertEquals(EtatPlace.LIBRE, a1.etat()); // non utilisé : remplacerPolygones garde l'état en base
        assertNull(a1.rectCm());
    }

    @Test
    void parkingMisAJourSansPerdreLePlan() {
        Parking p = ConversionPolygones.parking(fichier(), EXISTANT);
        assertEquals("Maquette SUP'COM", p.nom());
        assertEquals(800, p.largeurPx());
        assertEquals(600, p.hauteurPx());
        assertEquals(60, p.largeurCm());
        assertEquals(2, p.zonesInterdites().size());
        ZoneInterdite allee = p.zonesInterdites().get(0);
        assertEquals(new RectCm(50, 13, 10, 14), allee.rectCm()); // rect_cm du plan conservé
        assertEquals(new Point(639, 210), allee.poly().get(1));   // polygone remplacé
        assertNull(p.zonesInterdites().get(1).rectCm());          // nouvelle zone : pas encore de rect_cm
    }

    @Test
    void allerRetourAuFormatDuContrat() {
        Parking p = ConversionPolygones.parking(fichier(), EXISTANT);
        JsonObject relu = ConversionPolygones.fichier(p, ConversionPolygones.places(fichier()));
        assertEquals("P1", relu.getString("parking"));
        assertEquals(800, relu.getJsonArray("resolution").getInt(0));
        assertEquals("A1", relu.getJsonArray("places").getJsonObject(0).getString("id")); // trié par id
        assertEquals("pmr", relu.getJsonArray("places").getJsonObject(0).getString("type"));
        assertEquals(List.of("id", "type", "duree_max_min", "poly"),
                List.copyOf(relu.getJsonArray("places").getJsonObject(0).keySet()));
        assertEquals(List.of("id", "poly"), List.copyOf(relu.getJsonArray("zones_interdites").getJsonObject(0).keySet()));
        assertEquals(List.of(), ValidationPolygones.valider(
                Json.createReader(new StringReader(relu.toString().replace("800", "640").replace("600", "480"))).readValue())
                .stream().filter(e -> !e.contains("sortie")).toList());
    }
}
