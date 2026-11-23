package tn.supcom.smartparking.core.model;

/** Who detected the anomaly: the Pi (straddling, forbidden zone) or the server (overstay, special spot). */
public enum EventSource {
    PI("pi"),
    SERVER("serveur");

    private final String value;

    EventSource(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /** @throws IllegalArgumentException if the value is unknown */
    public static EventSource fromValue(String value) {
        for (EventSource s : values()) {
            if (s.value.equals(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown event source: " + value);
    }
}
