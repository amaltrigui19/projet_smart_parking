package tn.supcom.smartparking.core.exception;

import java.util.List;

/** The calibration file breaks one or more rules (HTTP 400, code {@code config_invalide}). */
public class InvalidConfigException extends BusinessException {

    /** Separator between errors in the message: the calibration page splits the message on it. */
    public static final String SEPARATOR = " ; ";

    private final List<String> errors;

    public InvalidConfigException(List<String> errors) {
        super("config_invalide", String.join(SEPARATOR, errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
