package tn.supcom.smartparking.core.exception;

/** The request itself is malformed, e.g. a missing or invalid JSON body (HTTP 400, code {@code requete_invalide}). */
public class InvalidRequestException extends BusinessException {
    public InvalidRequestException(String message) {
        super("requete_invalide", message);
    }
}
