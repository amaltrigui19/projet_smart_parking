package tn.supcom.smartparking.core.persistence;

import java.util.List;
import java.util.Optional;
import tn.supcom.smartparking.core.model.Reservation;
import tn.supcom.smartparking.core.model.ReservationState;

/** Storage of reservations. Implemented with MongoDB by {@link MongoReservationRepository}. */
public interface ReservationRepository {

    /** Maximum size of a list (contract §2.8: 100 at most). */
    int MAX_LIMIT = 100;

    /**
     * Saves a new reservation and returns it with its id ("r_…").
     *
     * @throws ReservationConflictException if the driver or the spot already has an open reservation
     */
    Reservation create(Reservation reservation);

    Optional<Reservation> findById(String id);

    /**
     * Newest first (GET /api/reservations).
     *
     * @param userId null = all (operator)
     * @param state  null = all states
     * @param limit  clamped between 1 and {@link #MAX_LIMIT}
     */
    List<Reservation> list(String userId, ReservationState state, int limit);

    /** The active or in-progress reservation of the driver, if any. */
    Optional<Reservation> findOpenByUser(String userId);

    /** The active or in-progress reservation on the spot, if any (map: "reserved" spot). */
    Optional<Reservation> findOpenBySpot(String spotId);

    /** All open reservations: for the map and the expiry of active reservations. */
    List<Reservation> findOpen();

    /**
     * Replaces the reservation, only if its state in the database is still {@code expectedState}.
     * Prevents a cancellation and an arrival at the same time from overwriting each other.
     *
     * @return false if the reservation does not exist or its state changed in between
     * @throws ReservationConflictException if the new state is open and conflicts
     */
    boolean update(Reservation reservation, ReservationState expectedState);
}
