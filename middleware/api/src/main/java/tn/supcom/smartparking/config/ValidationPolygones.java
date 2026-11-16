package tn.supcom.smartparking.config;

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
 * Règles de l'erreur {@code 400 config_invalide} de PUT /api/config/polygones (contrat §2.10, format §5.2).
 * Ce sont les mêmes règles que frontend/outils/valider_places.py, la fausse API et la page de calibration
 * (js/validation-places.js) :
 * identifiant en double, polygone de moins de 3 points, hors de la résolution ou plat, type inconnu,
 * durée maximale ≤ 0, place absente du plan. En plus : le parking doit être P1 (un seul parking, §2.1).
 */
public final class ValidationPolygones {

    /** Le seul parking du projet (contrat §2.1). */
    public static final String PARKING = "P1";
    /** Places du plan (contrat §4.3). Plus tard, elles viendront de GET /api/config/plan (sprint 6). */
    public static final List<String> PLAN_IDS = List.of("A1", "A2", "A3", "A4", "B1", "B2", "B3", "B4");
    /** Séparateur des erreurs dans le message : la page de calibration découpe le message sur ce texte. */
    public static final String SEPARATEUR = " ; ";

    private static final Set<String> TYPES = Set.of("normale", "pmr", "electrique");
    private static final Pattern ID_VALIDE = Pattern.compile("^[A-Za-z0-9_-]+$");

    private ValidationPolygones() {
    }

    /** Liste des erreurs ; vide si le fichier est conforme. */
    public static List<String> valider(JsonValue racine) {
        List<String> erreurs = new ArrayList<>();
        if (racine == null || racine.getValueType() != JsonValue.ValueType.OBJECT) {
            erreurs.add("le fichier doit être un objet JSON");
            return erreurs;
        }
        JsonObject d = racine.asJsonObject();

        String parking = texte(d.get("parking"));
        if (parking == null || parking.isEmpty()) {
            erreurs.add("'parking' doit être un texte non vide, par exemple \"P1\"");
        } else if (!PARKING.equals(parking)) {
            erreurs.add("'parking' doit être \"" + PARKING + "\" (un seul parking dans le projet)");
        }

        long largeur = Long.MAX_VALUE;
        long hauteur = Long.MAX_VALUE;
        JsonValue res = d.get("resolution");
        if (res instanceof JsonArray r && r.size() == 2 && entierPositif(r.get(0)) && entierPositif(r.get(1))) {
            largeur = ((JsonNumber) r.get(0)).longValue();
            hauteur = ((JsonNumber) r.get(1)).longValue();
        } else {
            erreurs.add("'resolution' doit être [largeur, hauteur] en pixels");
        }

        Set<String> vus = new HashSet<>();
        JsonValue places = d.get("places");
        if (places instanceof JsonArray liste) {
            for (int i = 0; i < liste.size(); i++) {
                if (!(liste.get(i) instanceof JsonObject p)) {
                    erreurs.add("places[" + i + "] doit être un objet");
                    continue;
                }
                String nom = verifierId("places[" + i + "]", p, vus, erreurs);
                String type = texte(p.get("type"));
                if (type == null || !TYPES.contains(type)) {
                    erreurs.add(nom + " : 'type' doit être l'un de [electrique, normale, pmr]");
                }
                JsonValue duree = p.get("duree_max_min");
                if (!(entier(duree) && ((JsonNumber) duree).longValue() >= 1)) {
                    erreurs.add(nom + " : 'duree_max_min' doit être un entier ≥ 1");
                }
                String id = texte(p.get("id"));
                if (id != null && ID_VALIDE.matcher(id).matches() && !PLAN_IDS.contains(id)) {
                    erreurs.add(id + " : cette place n'existe pas dans le plan (" + String.join(", ", PLAN_IDS) + ")");
                }
                verifierPoly(nom, p.get("poly"), largeur, hauteur, erreurs);
            }
        } else {
            erreurs.add("'places' doit être une liste");
        }

        JsonValue zones = d.get("zones_interdites");
        if (zones instanceof JsonArray liste) {
            for (int i = 0; i < liste.size(); i++) {
                if (!(liste.get(i) instanceof JsonObject z)) {
                    erreurs.add("zones_interdites[" + i + "] doit être un objet");
                    continue;
                }
                verifierPoly(verifierId("zones_interdites[" + i + "]", z, vus, erreurs), z.get("poly"), largeur, hauteur, erreurs);
            }
        } else {
            erreurs.add("'zones_interdites' doit être une liste");
        }
        return erreurs;
    }

    private static String verifierId(String nom, JsonObject element, Set<String> vus, List<String> erreurs) {
        String id = texte(element.get("id"));
        if (id == null || !ID_VALIDE.matcher(id).matches()) {
            erreurs.add(nom + " : 'id' manquant ou invalide");
            return nom;
        }
        if (!vus.add(id)) {
            erreurs.add(id + " : identifiant en double");
        }
        return id;
    }

    private static void verifierPoly(String nom, JsonValue valeur, long largeur, long hauteur, List<String> erreurs) {
        if (!(valeur instanceof JsonArray poly) || poly.size() < 3) {
            erreurs.add(nom + " : 'poly' doit être une liste d'au moins 3 points");
            return;
        }
        long[][] points = new long[poly.size()][];
        for (int i = 0; i < poly.size(); i++) {
            if (!(poly.get(i) instanceof JsonArray p && p.size() == 2 && entier(p.get(0)) && entier(p.get(1)))) {
                erreurs.add(nom + " : le point " + i + " doit être [x, y] en pixels entiers");
                return;
            }
            long x = ((JsonNumber) p.get(0)).longValue();
            long y = ((JsonNumber) p.get(1)).longValue();
            points[i] = new long[] {x, y};
            if (x < 0 || x >= largeur || y < 0 || y >= hauteur) {
                erreurs.add(nom + " : le point " + i + " [" + x + ", " + y + "] sort de l'image " + largeur + " × " + hauteur);
            }
        }
        if (aireDouble(points) == 0) {
            erreurs.add(nom + " : le polygone est plat (aire nulle)");
        }
    }

    /** Deux fois l'aire du polygone (formule du lacet) : nulle si le polygone est plat. */
    static long aireDouble(long[][] p) {
        long s = 0;
        for (int i = 0; i < p.length; i++) {
            long[] a = p[i];
            long[] b = p[(i + 1) % p.length];
            s += a[0] * b[1] - b[0] * a[1];
        }
        return Math.abs(s);
    }

    private static String texte(JsonValue v) {
        return v instanceof JsonString s ? s.getString() : null;
    }

    private static boolean entier(JsonValue v) {
        return v instanceof JsonNumber n && n.isIntegral();
    }

    private static boolean entierPositif(JsonValue v) {
        return entier(v) && ((JsonNumber) v).longValue() > 0;
    }
}
