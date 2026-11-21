package tn.supcom.smartparking.core.model;

/** Kind of parking spot; {@link #value()} is the text stored in MongoDB and exchanged as JSON. */
public enum SpotType {
    STANDARD("normale"),
    DISABLED("pmr"),
    ELECTRIC("electrique");

    private final String value;

    SpotType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /** @throws IllegalArgumentException if the value is unknown */
    public static SpotType fromValue(String value) {
        for (SpotType t : values()) {
            if (t.value.equals(value)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown spot type: " + value);
    }
}
