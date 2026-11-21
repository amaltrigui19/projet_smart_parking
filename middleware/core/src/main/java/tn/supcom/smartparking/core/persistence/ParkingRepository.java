package tn.supcom.smartparking.core.persistence;

import java.util.Optional;
import tn.supcom.smartparking.core.model.Parking;

/** Storage of parkings. Implemented with MongoDB by {@link MongoParkingRepository}. */
public interface ParkingRepository {

    Optional<Parking> findById(String id);

    /**
     * Creates or updates the parking. Only the model fields are written, so data added later
     * (guidance graph, settings, Pi status) is not erased.
     */
    void save(Parking parking);
}
