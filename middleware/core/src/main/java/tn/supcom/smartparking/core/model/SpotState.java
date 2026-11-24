package tn.supcom.smartparking.core.model;

/**
 * State of a spot. FREE and OCCUPIED are sent by the Raspberry Pi (contract §5.1) and stored in MongoDB.
 * RESERVED is never stored: the server computes it for a free spot with an open reservation (contract §2.6).
 */
public enum SpotState {
    FREE("free"),
    OCCUPIED("occupied"),
    RESERVED("reserved");

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
