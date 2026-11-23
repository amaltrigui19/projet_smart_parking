package tn.supcom.smartparking.core.model;

import java.util.Set;

/**
 * States of a reservation (contract §2.8):
 * active → in progress → completed → paid; active → cancelled or expired; in progress → paid.
 */
public enum ReservationState {
    ACTIVE("active"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),
    PAID("paid"),
    CANCELLED("cancelled"),
    EXPIRED("expired");

    /** A driver has at most one reservation in these states, and so does a spot. */
    public static final Set<ReservationState> OPEN = Set.of(ACTIVE, IN_PROGRESS);

    private final String value;

    ReservationState(String value) {
        this.value = value;
    }

    /** Value stored in MongoDB and exchanged as JSON. */
    public String value() {
        return value;
    }

    /** @throws IllegalArgumentException if the value is unknown */
    public static ReservationState fromValue(String value) {
        for (ReservationState s : values()) {
            if (s.value.equals(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown reservation state: " + value);
    }

    public boolean isOpen() {
        return OPEN.contains(this);
    }
}
