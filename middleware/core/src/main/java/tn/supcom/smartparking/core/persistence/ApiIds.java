package tn.supcom.smartparking.core.persistence;

import java.util.Optional;
import org.bson.types.ObjectId;

/**
 * API ids (contract §2.1): an {@code ObjectId} in the database; in the API, a prefix
 * followed by the ObjectId in hexadecimal (e.g. "r_6543a1f0…").
 */
public final class ApiIds {

    public static final String USER = "u_";
    public static final String RESERVATION = "r_";
    public static final String EVENT = "evt_";
    public static final String PERMISSION = "perm_";

    private ApiIds() {
    }

    public static String toApi(String prefix, ObjectId id) {
        return id == null ? null : prefix + id.toHexString();
    }

    /** The ObjectId of an API id; empty if it is null, has the wrong prefix or is malformed. */
    public static Optional<ObjectId> fromApi(String prefix, String id) {
        if (id == null || !id.startsWith(prefix)) {
            return Optional.empty();
        }
        String hex = id.substring(prefix.length());
        return ObjectId.isValid(hex) ? Optional.of(new ObjectId(hex)) : Optional.empty();
    }

    /** Like {@link #fromApi}, but throws IllegalArgumentException: for required references. */
    static ObjectId require(String prefix, String id) {
        return fromApi(prefix, id).orElseThrow(
                () -> new IllegalArgumentException("invalid id (expected " + prefix + "…): " + id));
    }

    /** Like {@link #require}, but null stays null (optional references). */
    static ObjectId optional(String prefix, String id) {
        return id == null ? null : require(prefix, id);
    }
}
