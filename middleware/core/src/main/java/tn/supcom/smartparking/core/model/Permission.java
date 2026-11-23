package tn.supcom.smartparking.core.model;

import java.time.Instant;

/**
 * An accessible-spot or electric-spot permission, as stored in the {@code autorisations} collection (contract §2.14).
 *
 * @param id        API id ("aut_" + ObjectId); null before it is saved
 * @param userId    API id of the driver ("u_…")
 * @param end       null = no end
 * @param grantedBy API id of the operator ("u_…"); null for sample data
 */
public record Permission(String id, String userId, PermissionType type,
                         Instant start, Instant end, String grantedBy) {

    public Permission {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("permission: missing user");
        if (type == null) throw new IllegalArgumentException("permission: missing type");
        if (start == null) throw new IllegalArgumentException("permission: missing start");
        if (end != null && !end.isAfter(start)) throw new IllegalArgumentException("permission: end must be after start");
    }

    /** True if the permission is in force at the given instant (start included, end excluded). */
    public boolean isValidAt(Instant instant) {
        return !instant.isBefore(start) && (end == null || instant.isBefore(end));
    }

    /** The same permission with its id, once saved. */
    public Permission withId(String newId) {
        return new Permission(newId, userId, type, start, end, grantedBy);
    }
}
