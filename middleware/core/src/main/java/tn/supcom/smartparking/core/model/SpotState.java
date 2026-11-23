package tn.supcom.smartparking.core.model;

/** Occupancy state of a spot, sent by the Raspberry Pi (contract §5.1). */
public enum SpotState {
    FREE("free"),
    OCCUPIED("occupied");

    private final String value;

    SpotState(String value) {
        this.value = value;
    }

    /** Value stored in MongoDB and exchanged as JSON (contract wording). */
    public String value() {
        return value;
    }

    public static SpotState fromValue(String value) {
        for (SpotState s : values()) {
            if (s.value.equals(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown spot state: " + value);
    }
}
