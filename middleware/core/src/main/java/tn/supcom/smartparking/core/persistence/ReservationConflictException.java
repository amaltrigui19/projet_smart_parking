package tn.supcom.smartparking.core.persistence;

/**
 * The database refused a second open reservation (active or in progress), thanks to a partial unique index.
 * The API turns it into {@code 409 reservation_deja_active} or {@code 409 place_indisponible} (contract §2.8).
 */
public class ReservationConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum Reason {
        /** The driver already has an open reservation. */
        USER,
        /** The spot already has an open reservation. */
        SPOT
    }

    private final Reason reason;

    public ReservationConflictException(Reason reason) {
        super(reason == Reason.USER ? "the driver already has an open reservation"
                : "the spot already has an open reservation");
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
