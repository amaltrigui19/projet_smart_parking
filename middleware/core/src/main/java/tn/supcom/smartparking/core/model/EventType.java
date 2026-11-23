package tn.supcom.smartparking.core.model;

/** Type of an anomaly (contract §2.9): the first two come from the Pi, the other two from the server. */
public enum EventType {
    STRADDLING("straddling"),
    FORBIDDEN_ZONE("forbidden_zone"),
    OVERSTAY("overstay"),
    UNAUTHORIZED_SPECIAL_SPOT("unauthorized_special_spot");

    private final String value;

    EventType(String value) {
        this.value = value;
    }

    /** Value stored in MongoDB and exchanged as JSON. */
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
