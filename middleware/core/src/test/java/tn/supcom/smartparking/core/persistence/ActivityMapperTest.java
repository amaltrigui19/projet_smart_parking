package tn.supcom.smartparking.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import tn.supcom.smartparking.core.model.Event;
import tn.supcom.smartparking.core.model.EventSource;
import tn.supcom.smartparking.core.model.EventType;
import tn.supcom.smartparking.core.model.Payment;
import tn.supcom.smartparking.core.model.Permission;
import tn.supcom.smartparking.core.model.PermissionType;
import tn.supcom.smartparking.core.model.Reservation;
import tn.supcom.smartparking.core.model.ReservationState;

class ActivityMapperTest {

    private static final ObjectId ID = new ObjectId("64e0000000000000000b0001");
    private static final String SARA = "u_6543a1f0aabbccddeeff0011";
    private static final String OPERATOR = "u_6543a1f0aabbccddeeff0099";
    private static final Instant T = Instant.parse("2026-11-20T09:58:40Z");

    private static Reservation paid() {
        return new Reservation("r_" + ID.toHexString(), SARA, "B2", ReservationState.PAID,
                T, T.plusSeconds(900), T.plusSeconds(145), T.plusSeconds(4910), 2.25, "TND",
                new Payment("simulated", T.plusSeconds(4940), "RECU-2026-000042"));
    }

    // ---------- Reservations ----------

    @Test
    void reservationRoundTripWithoutLoss() {
        Reservation r = paid();
        assertEquals(r, ActivityMapper.toReservation(ActivityMapper.toDocument(r, ID)));
    }

    @Test
    void reservationFieldsNamedAsInContract() {
        Document d = ActivityMapper.toDocument(paid(), ID);
        assertEquals(ID, d.get("_id"));
        assertEquals(new ObjectId("6543a1f0aabbccddeeff0011"), d.get("user")); // ObjectId, like users._id
        assertEquals("paid", d.get("state"));
        assertInstanceOf(Date.class, d.get("created_at"));
        assertInstanceOf(Date.class, d.get("expires_at"));
        Document p = d.get("payment", Document.class);
        assertEquals("simulated", p.get("method"));
        assertEquals("RECU-2026-000042", p.get("receipt"));
        assertInstanceOf(Date.class, p.get("paid_at"));
    }

    @Test
    void activeReservationWithoutPayment() {
        Reservation r = Reservation.create(SARA, "B3", T, 15).withId("r_" + ID.toHexString());
        Document d = ActivityMapper.toDocument(r, ID);
        assertEquals("active", d.get("state"));
        assertEquals(Date.from(T.plusSeconds(900)), d.get("expires_at"));
        assertTrue(d.containsKey("payment"));   // present and null, as in the API response
        assertNull(d.get("payment"));
        assertEquals(r, ActivityMapper.toReservation(d));
    }

    @Test
    void inProgressValue() {
        Document d = ActivityMapper.toDocument(paid().withState(ReservationState.IN_PROGRESS), ID);
        assertEquals("in_progress", d.get("state"));
    }

    @Test
    void integerAmountWrittenByMongoshAccepted() {
        Document d = ActivityMapper.toDocument(paid(), ID).append("amount", 3);
        assertEquals(3.0, ActivityMapper.toReservation(d).amount());
    }

    @Test
    void malformedUserRejectedBeforeDatabase() {
        Reservation r = new Reservation(null, "sara", "B2", ReservationState.ACTIVE, T, T.plusSeconds(900),
                null, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> ActivityMapper.toDocument(r, ID));
    }

    // ---------- Permissions ----------

    @Test
    void permissionRoundTripWithoutLoss() {
        Permission p = new Permission("perm_" + ID.toHexString(), SARA, PermissionType.ACCESSIBLE,
                T, null, OPERATOR);
        Document d = ActivityMapper.toDocument(p, ID);
        assertEquals("accessible", d.get("type"));
        assertTrue(d.containsKey("end"));
        assertNull(d.get("end"));
        assertInstanceOf(ObjectId.class, d.get("granted_by"));
        assertEquals(p, ActivityMapper.toPermission(d));
    }

    @Test
    void permissionWithoutOperator() {
        Permission p = new Permission("perm_" + ID.toHexString(), SARA, PermissionType.ELECTRIC,
                T, T.plusSeconds(86400), null);
        Document d = ActivityMapper.toDocument(p, ID);
        assertNull(d.get("granted_by"));
        assertEquals(p, ActivityMapper.toPermission(d));
    }

    // ---------- Events ----------

    @Test
    void eventRoundTripWithDetails() {
        Event e = new Event("evt_" + ID.toHexString(), "P1", EventType.OVERSTAY, List.of("A2"), null,
                T, null, EventSource.SERVER, Map.of("duration_min", 121, "max_duration_min", 120),
                true, OPERATOR, T.plusSeconds(60), "Conducteur prévenu");
        Document d = ActivityMapper.toDocument(e, ID);
        assertEquals("overstay", d.get("type"));
        assertEquals("server", d.get("source"));
        assertEquals(121, d.get("details", Document.class).get("duration_min"));
        assertEquals(e, ActivityMapper.toEvent(d));
    }

    @Test
    void forbiddenZoneEventWithoutSpotOrDetails() {
        Event e = Event.create("P1", EventType.FORBIDDEN_ZONE, null, "allee1", T, EventSource.PI, null)
                .withId("evt_" + ID.toHexString());
        Document d = ActivityMapper.toDocument(e, ID);
        assertEquals("forbidden_zone", d.get("type"));
        assertEquals(List.of(), d.get("spots"));
        assertNull(d.get("details"));
        assertEquals(false, d.get("handled"));
        assertEquals(e, ActivityMapper.toEvent(d));
    }

    @Test
    void eventWrittenByMongoshWithoutOptionalFields() {
        // Minimal document, as if another tool had written it: missing fields are read as empty
        Document d = new Document("_id", ID).append("parking", "P1").append("type", "straddling")
                .append("spots", List.of("A3", "A4")).append("start", Date.from(T)).append("source", "pi");
        Event e = ActivityMapper.toEvent(d);
        assertEquals(List.of("A3", "A4"), e.spotIds());
        assertEquals(Map.of(), e.details());
        assertEquals(false, e.handled());
        assertNull(e.handledBy());
    }
}
