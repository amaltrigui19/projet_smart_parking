package tn.supcom.smartparking.core.model;

/** Type of an anomaly (contract §2.9): the first two come from the Pi, the other two from the server. */
public enum EventType {
    STRADDLING("cheval"),
    FORBIDDEN_ZONE("zone_interdite"),
    OVERSTAY("ventouse"),
    UNAUTHORIZED_SPECIAL_SPOT("place_speciale");

    private final String value;

    EventType(String value) {
        this.value = value;
    }

    /** Value stored in MongoDB and exchanged as JSON (current contract wording). */
    public String value() {
        return value;
    }

    /** @throws IllegalArgumentException if the value is unknown */
    public static EventType fromValue(String value) {
        for (EventType t : values()) {
            if (t.value.equals(value)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown event type: " + value);
    }
}
