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
 * Converts between Java models and the MongoDB documents of the {@code permissions}, {@code reservations}
 * and {@code events} collections (contract §2.14). No database access.
 *
 * <p>References to a user ({@code user}, {@code granted_by}, {@code handled_by}) are stored as
 * {@code ObjectId}, like the {@code _id} of the users collection.
 * Field names follow the English contract (snake_case).
 */
final class ActivityMapper {

    private ActivityMapper() {
    }

    // ---------- Permissions ----------

    static Document toDocument(Permission p, ObjectId id) {
        return new Document("_id", id)
                .append("user", ApiIds.require(ApiIds.USER, p.userId()))
                .append("type", p.type().value())
                .append("start", date(p.start()))
                .append("end", date(p.end()))
                .append("granted_by", ApiIds.optional(ApiIds.USER, p.grantedBy()));
    }

    static Permission toPermission(Document d) {
        return new Permission(
                ApiIds.toApi(ApiIds.PERMISSION, d.getObjectId("_id")),
                ApiIds.toApi(ApiIds.USER, d.getObjectId("user")),
                PermissionType.fromValue(d.getString("type")),
                instant(d.getDate("start")),
                instant(d.getDate("end")),
                ApiIds.toApi(ApiIds.USER, d.getObjectId("granted_by")));
    }

    // ---------- Reservations ----------

    static Document toDocument(Reservation r, ObjectId id) {
        Payment p = r.payment();
        return new Document("_id", id)
                .append("user", ApiIds.require(ApiIds.USER, r.userId()))
                .append("spot", r.spotId())
                .append("state", r.state().value())
                .append("created_at", date(r.createdAt()))
                .append("expires_at", date(r.expiresAt()))
                .append("start", date(r.start()))
                .append("end", date(r.end()))
                .append("amount", r.amount())
                .append("currency", r.currency())
                .append("payment", p == null ? null : new Document("method", p.method())
                        .append("paid_at", date(p.paidAt()))
                        .append("receipt", p.receipt()));
    }

    static Reservation toReservation(Document d) {
        Document p = d.get("payment", Document.class);
        Object amount = d.get("amount");
        return new Reservation(
                ApiIds.toApi(ApiIds.RESERVATION, d.getObjectId("_id")),
                ApiIds.toApi(ApiIds.USER, d.getObjectId("user")),
                d.getString("spot"),
                ReservationState.fromValue(d.getString("state")),
                instant(d.getDate("created_at")),
                instant(d.getDate("expires_at")),
                instant(d.getDate("start")),
                instant(d.getDate("end")),
                // mongosh writes 2.25 as a Double but 2 as an Integer: accept both
                amount instanceof Number n ? n.doubleValue() : null,
                d.getString("currency"),
                p == null ? null : new Payment(p.getString("method"), instant(p.getDate("paid_at")), p.getString("receipt")));
    }

    // ---------- Events ----------

    static Document toDocument(Event e, ObjectId id) {
        return new Document("_id", id)
                .append("parking", e.parkingId())
                .append("type", e.type().value())
                .append("spots", e.spotIds())
                .append("zone", e.zone())
                .append("start", date(e.start()))
                .append("end", date(e.end()))
                .append("source", e.source().value())
                .append("details", e.details().isEmpty() ? null : new Document(e.details()))
                .append("handled", e.handled())
                .append("handled_by", ApiIds.optional(ApiIds.USER, e.handledBy()))
                .append("handled_at", date(e.handledAt()))
                .append("comment", e.comment());
    }

    static Event toEvent(Document d) {
        List<String> spotIds = new ArrayList<>();
        if (d.get("spots") instanceof List<?> list) {
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
                instant(d.getDate("start")),
                instant(d.getDate("end")),
                EventSource.fromValue(d.getString("source")),
                details,
                Boolean.TRUE.equals(d.getBoolean("handled")),
                ApiIds.toApi(ApiIds.USER, d.getObjectId("handled_by")),
                instant(d.getDate("handled_at")),
                d.getString("comment"));
    }

    // ---------- Helpers ----------

    static Date date(Instant i) {
        return i == null ? null : Date.from(i);
    }

    static Instant instant(Date d) {
        return d == null ? null : d.toInstant();
    }
}
