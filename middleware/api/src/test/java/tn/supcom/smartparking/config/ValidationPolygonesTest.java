package tn.supcom.smartparking.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Mêmes cas que frontend/outils/test_validation.js et test_fausse_api.py (règles de config_invalide). */
class ValidationPolygonesTest {

    private static final String VALIDE = """
        {"parking": "P1", "resolution": [640, 480],
         "places": [
           {"id": "A1", "type": "pmr", "duree_max_min": 120, "poly": [[40,60],[130,60],[130,200],[40,200]]},
           {"id": "B4", "type": "electrique", "duree_max_min": 60, "poly": [[340,280],[430,280],[430,420],[340,420]]}],
         "zones_interdites": [{"id": "allee1", "poly": [[500,210],[639,210],[639,280],[500,280]]}]}
        """;

    private static JsonValue json(String texte) {
        return Json.createReader(new StringReader(texte)).readValue();
    }

    private static List<String> erreurs(String remplacer, String par) {
        assertTrue(VALIDE.contains(remplacer), "le cas de test ne modifie rien : " + remplacer);
        return ValidationPolygones.valider(json(VALIDE.replace(remplacer, par)));
    }

    private static void contient(List<String> erreurs, String attendu) {
        assertTrue(erreurs.stream().anyMatch(e -> e.contains(attendu)), "attendu « " + attendu + " », obtenu " + erreurs);
    }

    @Test
    void fichierValide() {
        assertEquals(List.of(), ValidationPolygones.valider(json(VALIDE)));
    }

    @Test
    void identifiantEnDouble() {
        contient(erreurs("\"id\": \"B4\"", "\"id\": \"A1\""), "A1 : identifiant en double");
    }

    @Test
    void doublonEntrePlaceEtZone() {
        contient(erreurs("\"id\": \"allee1\"", "\"id\": \"A1\""), "identifiant en double");
    }

    @Test
    void polygoneDeDeuxPoints() {
        contient(erreurs("[[40,60],[130,60],[130,200],[40,200]]", "[[40,60],[130,60]]"), "au moins 3 points");
    }

    @Test
    void pointHorsDeLaResolution() {
        contient(erreurs("[130,60]", "[640,60]"), "sort de l'image 640 × 480");
    }

    @Test
    void pointNonEntier() {
        contient(erreurs("[130,60]", "[130.5,60]"), "pixels entiers");
    }

    @Test
    void polygonePlat() {
        contient(erreurs("[[40,60],[130,60],[130,200],[40,200]]", "[[1,1],[2,2],[3,3]]"), "plat");
    }

    @Test
    void typeInconnu() {
        contient(erreurs("\"type\": \"pmr\"", "\"type\": \"reservee\""), "'type' doit être");
    }

    @Test
    void dureeNulleOuNegative() {
        contient(erreurs("\"duree_max_min\": 120", "\"duree_max_min\": 0"), "duree_max_min");
        contient(erreurs("\"duree_max_min\": 120", "\"duree_max_min\": -5"), "duree_max_min");
        contient(erreurs("\"duree_max_min\": 120", "\"duree_max_min\": 120.5"), "duree_max_min");
    }

    @Test
    void placeAbsenteDuPlan() {
        contient(erreurs("\"id\": \"A1\"", "\"id\": \"P9\""), "P9 : cette place n'existe pas dans le plan");
    }

    @Test
    void autreParkingQueP1() {
        contient(erreurs("\"parking\": \"P1\"", "\"parking\": \"P2\""), "'parking' doit être \"P1\"");
    }

    @Test
    void resolutionManquanteOuInvalide() {
        contient(erreurs("\"resolution\": [640, 480]", "\"resolution\": [640]"), "'resolution'");
        contient(erreurs("\"resolution\": [640, 480]", "\"resolution\": [0, 480]"), "'resolution'");
    }

    @Test
    void listesManquantes() {
        List<String> e = ValidationPolygones.valider(json("{\"parking\": \"P1\", \"resolution\": [640, 480]}"));
        contient(e, "'places' doit être une liste");
        contient(e, "'zones_interdites' doit être une liste");
    }

    @Test
    void pasUnObjet() {
        contient(ValidationPolygones.valider(json("[1, 2]")), "objet JSON");
    }

    @Test
    void plusieursErreursDansLeMemeMessage() {
        String modifie = VALIDE.replace("\"id\": \"B4\"", "\"id\": \"A1\"").replace("\"duree_max_min\": 120", "\"duree_max_min\": 0");
        List<String> e = ValidationPolygones.valider(json(modifie));
        assertEquals(2, e.size(), e.toString());
        String message = String.join(ValidationPolygones.SEPARATEUR, e);
        assertEquals(2, message.split(" ; ").length); // la page de calibration découpe sur « ; »
    }

    @Test
    void aireDuPolygone() {
        assertEquals(2 * 90 * 140, ValidationPolygones.aireDouble(new long[][] {{40, 60}, {130, 60}, {130, 200}, {40, 200}}));
        assertEquals(0, ValidationPolygones.aireDouble(new long[][] {{1, 1}, {2, 2}, {3, 3}}));
    }

    @Test
    void fichierVraimentVide() {
        JsonObject vide = Json.createObjectBuilder().build();
        assertTrue(ValidationPolygones.valider(vide).size() >= 4);
    }
}
