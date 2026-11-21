package tn.supcom.smartparking.core.exception;

/** The requested resource does not exist (HTTP 404, code {@code introuvable}). */
public class NotFoundException extends BusinessException {
    public NotFoundException(String message) {
        super("introuvable", message);
    }
}
