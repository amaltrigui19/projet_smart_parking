package tn.supcom.smartparking.core.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import tn.supcom.smartparking.core.model.Spot;
import tn.supcom.smartparking.core.model.SpotState;

/** Storage of parking spots. Implemented with MongoDB by {@link MongoSpotRepository}. */
public interface SpotRepository {

    /** All spots of a parking, sorted by id (A1, A2, ..., B4). */
    List<Spot> findByParking(String parkingId);

    Optional<Spot> findById(String id);

    /** Creates the spot, or fully replaces it if it already exists. */
    void save(Spot spot);

    /**
     * Calibration: stores the polygons of places.json (contract §5.2). Updates type, max duration and
     * polygon but KEEPS the state reported by the Pi and the floor-plan data. Spots of the parking that
     * are missing from the list are deleted.
     */
    void replacePolygons(String parkingId, List<Spot> spots);

    /**
     * New occupancy state reported by the Pi (contract §5.1).
     *
     * @return false if the spot does not exist
     */
    boolean updateState(String id, SpotState state, Instant since);
}
