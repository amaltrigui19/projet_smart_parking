package tn.supcom.smartparking.core.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.types.ObjectId;
import tn.supcom.smartparking.core.model.Event;
import tn.supcom.smartparking.core.model.EventSource;
import tn.supcom.smartparking.core.model.EventType;
import tn.supcom.smartparking.core.model.Payment;
import tn.supcom.smartparking.core.model.Permission;
import tn.supcom.smartparking.core.model.PermissionType;
import tn.supcom.smartparking.core.model.Reservation;
import tn.supcom.smartparking.core.model.ReservationState;

/**
 * Converts between Java models and the MongoDB documents of the {@code autorisations}, {@code reservations}
 * and {@code evenements} collections (contract §2.14). No database access.
 *
 * <p>References to a user ({@code utilisateur}, {@code accordee_par}, {@code traite_par}) are stored as
 * {@code ObjectId}, like the {@code _id} of the users collection.
 * Field names stay those of the current contract until the English contract is applied.
 */
final class ActivityMapper {

    private ActivityMapper() {
    }

    // ---------- Permissions ----------

    static Document toDocument(Permission p, ObjectId id) {
        return new Document("_id", id)
                .append("utilisateur", ApiIds.require(ApiIds.USER, p.userId()))
                .append("type", p.type().value())
                .append("debut", date(p.start()))
                .append("fin", date(p.end()))
                .append("accordee_par", ApiIds.optional(ApiIds.USER, p.grantedBy()));
    }

    static Permission toPermission(Document d) {
        return new Permission(
                ApiIds.toApi(ApiIds.PERMISSION, d.getObjectId("_id")),
                ApiIds.toApi(ApiIds.USER, d.getObjectId("utilisateur")),
                PermissionType.fromValue(d.getString("type")),
                instant(d.getDate("debut")),
                instant(d.getDate("fin")),
                ApiIds.toApi(ApiIds.USER, d.getObjectId("accordee_par")));
    }

    // ---------- Reservations ----------

    static Document toDocument(Reservation r, ObjectId id) {
        Payment p = r.payment();
        return new Document("_id", id)
                .append("utilisateur", ApiIds.require(ApiIds.USER, r.userId()))
                .append("place", r.spotId())
                .append("etat", r.state().value())
                .append("creee_le", date(r.createdAt()))
                .append("expire_le", date(r.expiresAt()))
                .append("debut", date(r.start()))
                .append("fin", date(r.end()))
                .append("montant", r.amount())
                .append("devise", r.currency())
                .append("paiement", p == null ? null : new Document("moyen", p.method())
                        .append("paye_le", date(p.paidAt()))
                        .append("recu", p.receipt()));
    }

    static Reservation toReservation(Document d) {
        Document p = d.get("paiement", Document.class);
        Object amount = d.get("montant");
        return new Reservation(
                ApiIds.toApi(ApiIds.RESERVATION, d.getObjectId("_id")),
                ApiIds.toApi(ApiIds.USER, d.getObjectId("utilisateur")),
                d.getString("place"),
                ReservationState.fromValue(d.getString("etat")),
                instant(d.getDate("creee_le")),
                instant(d.getDate("expire_le")),
                instant(d.getDate("debut")),
                instant(d.getDate("fin")),
                // mongosh writes 2.25 as a Double but 2 as an Integer: accept both
                amount instanceof Number n ? n.doubleValue() : null,
                d.getString("devise"),
                p == null ? null : new Payment(p.getString("moyen"), instant(p.getDate("paye_le")), p.getString("recu")));
    }

    // ---------- Events ----------

    static Document toDocument(Event e, ObjectId id) {
        return new Document("_id", id)
                .append("parking", e.parkingId())
                .append("type", e.type().value())
                .append("places", e.spotIds())
                .append("zone", e.zone())
                .append("debut", date(e.start()))
                .append("fin", date(e.end()))
                .append("source", e.source().value())
                .append("details", e.details().isEmpty() ? null : new Document(e.details()))
                .append("traite", e.handled())
                .append("traite_par", ApiIds.optional(ApiIds.USER, e.handledBy()))
                .append("traite_le", date(e.handledAt()))
                .append("commentaire", e.comment());
    }

    static Event toEvent(Document d) {
        List<String> spotIds = new ArrayList<>();
        if (d.get("places") instanceof List<?> list) {
            for (Object o : list) {
                spotIds.add((String) o);
            }
        }
        Map<String, Object> details = new LinkedHashMap<>();
        if (d.get("details") instanceof Document det) {
            details.putAll(det);
        }
        return new Event(
                ApiIds.toApi(ApiIds.EVENT, d.getObjectId("_id")),
                d.getString("parking"),
                EventType.fromValue(d.getString("type")),
                spotIds,
                d.getString("zone"),
                instant(d.getDate("debut")),
                instant(d.getDate("fin")),
                EventSource.fromValue(d.getString("source")),
                details,
                Boolean.TRUE.equals(d.getBoolean("traite")),
                ApiIds.toApi(ApiIds.USER, d.getObjectId("traite_par")),
                instant(d.getDate("traite_le")),
                d.getString("commentaire"));
    }

    // ---------- Helpers ----------

    static Date date(Instant i) {
        return i == null ? null : Date.from(i);
    }

    static Instant instant(Date d) {
        return d == null ? null : d.toInstant();
    }
}
