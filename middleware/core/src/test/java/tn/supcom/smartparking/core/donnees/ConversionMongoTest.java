package tn.supcom.smartparking.core.donnees;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import tn.supcom.smartparking.core.modele.EtatPlace;
import tn.supcom.smartparking.core.modele.Parking;
import tn.supcom.smartparking.core.modele.Place;
import tn.supcom.smartparking.core.modele.Point;
import tn.supcom.smartparking.core.modele.RectCm;
import tn.supcom.smartparking.core.modele.TypePlace;
import tn.supcom.smartparking.core.modele.ZoneInterdite;

class ConversionMongoTest {

    private static final List<Point> CARRE =
            List.of(new Point(40, 60), new Point(130, 60), new Point(130, 200), new Point(40, 200));

    private static Place placeA3() {
        return new Place("A3", "P1", TypePlace.PMR, 120, CARRE, new RectCm(30, 0, 8, 13), "A3",
                EtatPlace.OCCUPEE, Instant.parse("2026-11-20T10:02:11Z"));
    }

    @Test
    void placeAllerRetourSansPerte() {
        Place p = placeA3();
        assertEquals(p, ConversionMongo.versPlace(ConversionMongo.versDocument(p)));
    }

    @Test
    void champsNommesCommeLeContrat() {
        Document d = ConversionMongo.versDocument(placeA3());
        assertEquals("A3", d.get("_id"));
        assertEquals("pmr", d.get("type"));
        assertEquals(120, d.get("duree_max_min"));
        assertEquals("occupee", d.get("etat"));
        assertEquals(List.of(40, 60), ((List<?>) d.get("poly")).get(0));
    }

    @Test
    void litLesNombresDecimauxEcritsParMongosh() {
        Document d = new Document("_id", "A1").append("parking", "P1").append("type", "normale")
                .append("duree_max_min", 120.0)
                .append("poly", List.of(List.of(149.0, 0.0), List.of(235.0, 0.0), List.of(235.0, 156.0)));
        Place p = ConversionMongo.versPlace(d);
        assertEquals(120, p.dureeMaxMin());
        assertEquals(new Point(235, 156), p.poly().get(2));
        assertEquals(EtatPlace.LIBRE, p.etat());   // pas d'état en base -> libre
        assertNull(p.rectCm());
        assertNull(p.depuis());
    }

    @Test
    void refuseUnTypeInconnu() {
        Document d = ConversionMongo.versDocument(placeA3()).append("type", "camion");
        assertThrows(IllegalArgumentException.class, () -> ConversionMongo.versPlace(d));
    }

    @Test
    void refuseUnPolygoneDeMoinsDe3Points() {
        assertThrows(IllegalArgumentException.class, () -> new Place("A1", "P1", TypePlace.NORMALE, 120,
                List.of(new Point(0, 0), new Point(10, 0)), null, null, null, null));
    }

    @Test
    void parkingAllerRetourSansPerte() {
        Parking p = new Parking("P1", "Maquette SUP'COM", 640, 480, 60, 40,
                List.of(new ZoneInterdite("allee1", CARRE, new RectCm(50, 13, 10, 14))),
                Instant.parse("2026-11-16T12:00:00Z"));
        Document d = ConversionMongo.champsParking(p).append("_id", "P1");
        assertEquals(p, ConversionMongo.versParking(d));
    }
}