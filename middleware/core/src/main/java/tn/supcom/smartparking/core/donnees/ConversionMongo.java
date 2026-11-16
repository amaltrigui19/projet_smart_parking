package tn.supcom.smartparking.core.donnees;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import tn.supcom.smartparking.core.modele.EtatPlace;
import tn.supcom.smartparking.core.modele.Parking;
import tn.supcom.smartparking.core.modele.Place;
import tn.supcom.smartparking.core.modele.Point;
import tn.supcom.smartparking.core.modele.RectCm;
import tn.supcom.smartparking.core.modele.TypePlace;
import tn.supcom.smartparking.core.modele.ZoneInterdite;

/** Traduction entre les modèles Java et les documents MongoDB (contrat §2.14). Sans accès à la base. */
final class ConversionMongo {

    private ConversionMongo() {
    }

    // ---------- Places ----------

    static Document versDocument(Place p) {
        return new Document("_id", p.id())
                .append("parking", p.parking())
                .append("type", p.type().valeur())
                .append("duree_max_min", p.dureeMaxMin())
                .append("poly", polyVersListe(p.poly()))
                .append("rect_cm", rectVersListe(p.rectCm()))
                .append("noeud", p.noeud())
                .append("etat", p.etat().valeur())
                .append("depuis", p.depuis() == null ? null : Date.from(p.depuis()));
    }

    static Place versPlace(Document d) {
        String etat = d.getString("etat");
        Date depuis = d.getDate("depuis");
        return new Place(
                d.getString("_id"),
                d.getString("parking"),
                TypePlace.depuis(d.getString("type")),
                nombre(d.get("duree_max_min")).intValue(),
                listeVersPoly(d.get("poly")),
                listeVersRect(d.get("rect_cm")),
                d.getString("noeud"),
                etat == null ? EtatPlace.LIBRE : EtatPlace.depuis(etat),
                depuis == null ? null : depuis.toInstant());
    }

    // ---------- Parkings ----------

    /** Champs du parking SANS _id : utilisés dans un $set (voir DepotParkings). */
    static Document champsParking(Parking p) {
        List<Document> zones = new ArrayList<>();
        for (ZoneInterdite z : p.zonesInterdites()) {
            zones.add(new Document("id", z.id())
                    .append("poly", polyVersListe(z.poly()))
                    .append("rect_cm", rectVersListe(z.rectCm())));
        }
        return new Document("nom", p.nom())
                .append("resolution", List.of(p.largeurPx(), p.hauteurPx()))
                .append("dimensions_cm", List.of(p.largeurCm(), p.hauteurCm()))
                .append("zones_interdites", zones)
                .append("maj_le", p.majLe() == null ? null : Date.from(p.majLe()));
    }

    static Parking versParking(Document d) {
        List<?> resolution = (List<?>) d.get("resolution");
        List<?> dimensions = (List<?>) d.get("dimensions_cm");
        List<ZoneInterdite> zones = new ArrayList<>();
        if (d.get("zones_interdites") instanceof List<?> liste) {
            for (Object o : liste) {
                Document z = (Document) o;
                zones.add(new ZoneInterdite(z.getString("id"), listeVersPoly(z.get("poly")), listeVersRect(z.get("rect_cm"))));
            }
        }
        Date majLe = d.getDate("maj_le");
        return new Parking(
                d.getString("_id"),
                d.getString("nom"),
                nombre(resolution.get(0)).intValue(),
                nombre(resolution.get(1)).intValue(),
                dimensions == null ? 0 : nombre(dimensions.get(0)).doubleValue(),
                dimensions == null ? 0 : nombre(dimensions.get(1)).doubleValue(),
                zones,
                majLe == null ? null : majLe.toInstant());
    }

    // ---------- Outils ----------

    /** [[x, y], ...] : le format de « poly » dans places.json (contrat 5.2). */
    static List<List<Integer>> polyVersListe(List<Point> poly) {
        List<List<Integer>> liste = new ArrayList<>();
        for (Point pt : poly) {
            liste.add(List.of(pt.x(), pt.y()));
        }
        return liste;
    }

    static List<Point> listeVersPoly(Object valeur) {
        List<Point> poly = new ArrayList<>();
        if (valeur instanceof List<?> points) {
            for (Object o : points) {
                List<?> xy = (List<?>) o;
                poly.add(new Point(nombre(xy.get(0)).intValue(), nombre(xy.get(1)).intValue()));
            }
        }
        return poly;
    }

    static List<Double> rectVersListe(RectCm r) {
        return r == null ? null : List.of(r.x(), r.y(), r.largeur(), r.hauteur());
    }

    static RectCm listeVersRect(Object valeur) {
        if (!(valeur instanceof List<?> l) || l.size() != 4) {
            return null;
        }
        return new RectCm(nombre(l.get(0)).doubleValue(), nombre(l.get(1)).doubleValue(),
                nombre(l.get(2)).doubleValue(), nombre(l.get(3)).doubleValue());
    }

    /** MongoDB peut rendre 120 (Integer) ou 120.0 (Double) selon qui a écrit : on accepte les deux. */
    private static Number nombre(Object o) {
        if (o instanceof Number n) {
            return n;
        }
        throw new IllegalStateException("nombre attendu en base, reçu : " + o);
    }
}