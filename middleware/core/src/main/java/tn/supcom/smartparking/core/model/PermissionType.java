package tn.supcom.smartparking.core.model;

/** Permission granted by the operator to use a special spot (contract §2.11). */
public enum PermissionType {
    ACCESSIBLE("accessible"),
    ELECTRIC("electric");

    private final String value;

    PermissionType(String value) {
        this.value = value;
    }

    /** Value stored in MongoDB and exchanged as JSON. */
    public String value() {
        return value;
    }

    /** @throws IllegalArgumentException if the value is unknown */
    public static PermissionType fromValue(String value) {
        for (PermissionType t : values()) {
            if (t.value.equals(value)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown permission type: " + value);
    }
}
