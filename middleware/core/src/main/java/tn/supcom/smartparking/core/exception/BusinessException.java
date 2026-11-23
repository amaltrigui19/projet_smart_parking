package tn.supcom.smartparking.core.exception;

/**
 * Base class of the business errors of the parking domain. Each subclass carries the short error code
 * of the contract (§2.1); the REST layer maps it to an HTTP status.
 * Kept separate from the IAM errors: the domain does not depend on the identity module.
 */
public abstract class BusinessException extends RuntimeException {

    private final String code;

    protected BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** Short error code of the contract, e.g. {@code invalid_config}. */
    public String code() {
        return code;
    }
}
