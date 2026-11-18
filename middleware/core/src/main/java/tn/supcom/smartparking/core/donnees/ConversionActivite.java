package tn.supcom.smartparking.core.donnees;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.types.ObjectId;
import tn.supcom.smartparking.core.modele.Autorisation;
import tn.supcom.smartparking.core.modele.EtatReservation;
import tn.supcom.smartparking.core.modele.Evenement;
import tn.supcom.smartparking.core.modele.Paiement;
import tn.supcom.smartparking.core.modele.Reservation;
import tn.supcom.smartparking.core.modele.SourceEvenement;
import tn.supcom.smartparking.core.modele.TypeAutorisation;
import tn.supcom.smartparking.core.modele.TypeEvenement;

/**
 * Traduction entre les modèles Java et les documents MongoDB des collections
 * {@code autorisations}, {@code reservations} et {@code evenements} (contrat §2.14). Sans accès à la base.
 *
 * <p>Les références à un utilisateur ({@code utilisateur}, {@code accordee_par}, {@code traite_par})
 * sont stockées en {@code ObjectId}, comme le {@code _id} de la collection {@code utilisateurs}.
 */
final class ConversionActivite {

    private ConversionActivite() {
    }

    // ---------- Autorisations ----------

    static Document versDocument(Autorisation a, ObjectId id) {
        return new Document("_id", id)
                .append("utilisateur", Identifiants.exiger(Identifiants.UTILISATEUR, a.utilisateur()))
                .append("type", a.type().valeur())
                .append("debut", date(a.debut()))
                .append("fin", date(a.fin()))
                .append("accordee_par", Identifiants.facultatif(Identifiants.UTILISATEUR, a.accordeePar()));
    }

    static Autorisation versAutorisation(Document d) {
        return new Autorisation(
                Identifiants.versApi(Identifiants.AUTORISATION, d.getObjectId("_id")),
                Identifiants.versApi(Identifiants.UTILISATEUR, d.getObjectId("utilisateur")),
                TypeAutorisation.depuis(d.getString("type")),
                instant(d.getDate("debut")),
                instant(d.getDate("fin")),
                Identifiants.versApi(Identifiants.UTILISATEUR, d.getObjectId("accordee_par")));
    }

    // ---------- Réservations ----------

    static Document versDocument(Reservation r, ObjectId id) {
        Paiement p = r.paiement();
        return new Document("_id", id)
                .append("utilisateur", Identifiants.exiger(Identifiants.UTILISATEUR, r.utilisateur()))
                .append("place", r.place())
                .append("etat", r.etat().valeur())
                .append("creee_le", date(r.creeeLe()))
                .append("expire_le", date(r.expireLe()))
                .append("debut", date(r.debut()))
                .append("fin", date(r.fin()))
                .append("montant", r.montant())
                .append("devise", r.devise())
                .append("paiement", p == null ? null : new Document("moyen", p.moyen())
                        .append("paye_le", date(p.payeLe()))
                        .append("recu", p.recu()));
    }

    static Reservation versReservation(Document d) {
        Document p = d.get("paiement", Document.class);
        Object montant = d.get("montant");
        return new Reservation(
                Identifiants.versApi(Identifiants.RESERVATION, d.getObjectId("_id")),
                Identifiants.versApi(Identifiants.UTILISATEUR, d.getObjectId("utilisateur")),
                d.getString("place"),
                EtatReservation.depuis(d.getString("etat")),
                instant(d.getDate("creee_le")),
                instant(d.getDate("expire_le")),
                instant(d.getDate("debut")),
                instant(d.getDate("fin")),
                // mongosh écrit 2.25 en Double mais 2 en Integer : on accepte les deux
                montant instanceof Number n ? n.doubleValue() : null,
                d.getString("devise"),
                p == null ? null : new Paiement(p.getString("moyen"), instant(p.getDate("paye_le")), p.getString("recu")));
    }

    // ---------- Événements ----------

    static Document versDocument(Evenement e, ObjectId id) {
        return new Document("_id", id)
                .append("parking", e.parking())
                .append("type", e.type().valeur())
                .append("places", e.places())
                .append("zone", e.zone())
                .append("debut", date(e.debut()))
                .append("fin", date(e.fin()))
                .append("source", e.source().valeur())
                .append("details", e.details().isEmpty() ? null : new Document(e.details()))
                .append("traite", e.traite())
                .append("traite_par", Identifiants.facultatif(Identifiants.UTILISATEUR, e.traitePar()))
                .append("traite_le", date(e.traiteLe()))
                .append("commentaire", e.commentaire());
    }

    static Evenement versEvenement(Document d) {
        List<String> places = new ArrayList<>();
        if (d.get("places") instanceof List<?> liste) {
            for (Object o : liste) {
                places.add((String) o);
            }
        }
        Map<String, Object> details = new LinkedHashMap<>();
        if (d.get("details") instanceof Document det) {
            details.putAll(det);
        }
        return new Evenement(
                Identifiants.versApi(Identifiants.EVENEMENT, d.getObjectId("_id")),
                d.getString("parking"),
                TypeEvenement.depuis(d.getString("type")),
                places,
                d.getString("zone"),
                instant(d.getDate("debut")),
                instant(d.getDate("fin")),
                SourceEvenement.depuis(d.getString("source")),
                details,
                Boolean.TRUE.equals(d.getBoolean("traite")),
                Identifiants.versApi(Identifiants.UTILISATEUR, d.getObjectId("traite_par")),
                instant(d.getDate("traite_le")),
                d.getString("commentaire"));
    }

    // ---------- Outils ----------

    static Date date(Instant i) {
        return i == null ? null : Date.from(i);
    }

    static Instant instant(Date d) {
        return d == null ? null : d.toInstant();
    }
}
