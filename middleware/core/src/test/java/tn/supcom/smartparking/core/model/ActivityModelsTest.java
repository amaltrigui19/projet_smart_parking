package tn.supcom.smartparking.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ActivityModelsTest {

    private static final String SARA = "u_6543a1f0aabbccddeeff0011";
    private static final Instant T = Instant.parse("2026-11-20T10:00:00Z");

    @Test
    void permissionValidBetweenStartAndEnd() {
        Permission p = new Permission(null, SARA, PermissionType.ACCESSIBLE, T, T.plusSeconds(3600), null);
        assertFalse(p.isValidAt(T.minusSeconds(1)));
        assertTrue(p.isValidAt(T));
        assertTrue(p.isValidAt(T.plusSeconds(3599)));
        assertFalse(p.isValidAt(T.plusSeconds(3600)));   // end excluded
        assertTrue(new Permission(null, SARA, PermissionType.ACCESSIBLE, T, null, null).isValidAt(T.plusSeconds(1_000_000)));
    }

    @Test
    void permissionEndBeforeStartRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Permission(null, SARA, PermissionType.ACCESSIBLE, T, T, null));
    }

    @Test
    void newReservationActiveFifteenMinutes() {
        Reservation r = Reservation.create(SARA, "B2", T, 15);
        assertEquals(ReservationState.ACTIVE, r.state());
        assertEquals(T.plusSeconds(900), r.expiresAt());
        assertTrue(r.state().isOpen());
    }

    @Test
    void openStates() {
        assertTrue(ReservationState.IN_PROGRESS.isOpen());
        for (ReservationState s : List.of(ReservationState.COMPLETED, ReservationState.PAID,
                ReservationState.CANCELLED, ReservationState.EXPIRED)) {
            assertFalse(s.isOpen(), s.name());
        }
        assertEquals(ReservationState.IN_PROGRESS, ReservationState.fromValue("in_progress"));
    }

    @Test
    void paidReservationWithoutPaymentRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Reservation(null, SARA, "B2", ReservationState.PAID,
                T, T.plusSeconds(900), T, T.plusSeconds(3600), 1.5, "TND", null));
    }

    @Test
    void eventDependsOnType() {
        assertThrows(IllegalArgumentException.class,   // straddling without spot
                () -> Event.create("P1", EventType.STRADDLING, List.of(), null, T, EventSource.PI, null));
        assertThrows(IllegalArgumentException.class,   // forbidden zone without zone
                () -> Event.create("P1", EventType.FORBIDDEN_ZONE, null, null, T, EventSource.PI, null));
        Event e = Event.create("P1", EventType.FORBIDDEN_ZONE, null, "allee1", T, EventSource.PI, null);
        assertEquals(List.of(), e.spotIds());
        assertFalse(e.handled());
    }

    @Test
    void detailsCopiedAndUnmodifiable() {
        Map<String, Object> details = new HashMap<>();
        details.put("duration_min", 121);
        details.put("note", null);   // a null value is accepted
        Event e = Event.create("P1", EventType.OVERSTAY, List.of("A2"), null, T, EventSource.SERVER, details);
        details.put("duration_min", 5);
        assertEquals(121, e.details().get("duration_min"));
        assertThrows(UnsupportedOperationException.class, () -> e.details().put("x", 1));
    }

    @Test
    void valuesLowercaseAsInContract() {
        assertEquals("forbidden_zone", EventType.FORBIDDEN_ZONE.value());
        assertEquals("unauthorized_special_spot", EventType.UNAUTHORIZED_SPECIAL_SPOT.value());
        assertEquals("electric", PermissionType.ELECTRIC.value());
        assertEquals(EventSource.PI, EventSource.fromValue("pi"));
    }
}
