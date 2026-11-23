package tn.supcom.smartparking.core.persistence;

import java.time.Instant;
import java.util.List;
import tn.supcom.smartparking.core.model.Permission;
import tn.supcom.smartparking.core.model.PermissionType;

/** Storage of accessible/electric permissions. Implemented with MongoDB by {@link MongoPermissionRepository}. */
public interface PermissionRepository {

    /** Saves a new permission and returns it with its id ("aut_…"). */
    Permission create(Permission permission);

    /** All permissions of a user (expired ones included), newest first. */
    List<Permission> findByUser(String userId);

    /** True if the user has a permission of this type in force at this instant (reservation, special spot). */
    boolean hasValid(String userId, PermissionType type, Instant instant);

    /** Removes permission {@code id} of the user; false if it does not exist or is not theirs. */
    boolean delete(String userId, String id);
}
