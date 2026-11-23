package tn.supcom.smartparking.core.model;

import java.time.Instant;

/**
 * A reservation, as stored in the {@code reservations} collection (contract §2.14).
 *
 * @param id        API id ("r_" + ObjectId); null before it is saved
 * @param userId    API id of the driver ("u_…")
 * @param spotId    spot id ("B2")
 * @param expiresAt after this time, a reservation still active becomes expired
 * @param start     arrival of the car; null until it is there
 * @param end       departure of the car; null while it is there
 * @param amount    null until the reservation is paid
 * @param payment   null until the reservation is paid
 */
public record Reservation(String id, String userId, String spotId, ReservationState state,
                          Instant createdAt, Instant expiresAt, Instant start, Instant end,
                          Double amount, String currency, Payment payment) {

    public Reservation {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("reservation: missing user");
        if (spotId == null || spotId.isBlank()) throw new IllegalArgumentException("reservation: missing spot");
        if (state == null) throw new IllegalArgumentException("reservation: missing state");
        if (createdAt == null || expiresAt == null) throw new IllegalArgumentException("reservation: created_at and expires_at are required");
        if (expiresAt.isBefore(createdAt)) throw new IllegalArgumentException("reservation: expires_at before created_at");
        if (start != null && end != null && end.isBefore(start)) throw new IllegalArgumentException("reservation: end before start");
        if (amount != null && amount < 0) throw new IllegalArgumentException("reservation: negative amount");
        if (state == ReservationState.PAID && payment == null) throw new IllegalArgumentException("reservation: paid without payment");
    }

    /** New reservation in the "active" state (POST /api/reservations), valid for {@code timeoutMin} minutes. */
    public static Reservation create(String userId, String spotId, Instant now, int timeoutMin) {
        return new Reservation(null, userId, spotId, ReservationState.ACTIVE, now,
                now.plusSeconds(60L * timeoutMin), null, null, null, null, null);
    }

    public Reservation withId(String newId) {
        return new Reservation(newId, userId, spotId, state, createdAt, expiresAt, start, end, amount, currency, payment);
    }

    public Reservation withState(ReservationState newState) {
        return new Reservation(id, userId, spotId, newState, createdAt, expiresAt, start, end, amount, currency, payment);
    }
}
