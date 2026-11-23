package tn.supcom.smartparking.core.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import tn.supcom.smartparking.core.model.Event;
import tn.supcom.smartparking.core.model.EventType;

/** Storage of anomalies. Implemented with MongoDB by {@link MongoEventRepository}. */
public interface EventRepository {

    /** Maximum size of a list. */
    int MAX_LIMIT = 100;

    /** Saves a new anomaly and returns it with its id ("evt_…"). */
    Event create(Event event);

    Optional<Event> findById(String id);

    /**
     * Newest first (GET /api/evenements). Each null filter is ignored.
     *
     * @param from  start included
     * @param to    end excluded
     * @param limit clamped between 1 and {@link #MAX_LIMIT}
     */
    List<Event> list(Boolean handled, EventType type, Instant from, Instant to, int limit);

    /** Number of unhandled anomalies (dashboard: alertes_non_traitees). */
    long countUnhandled();

    /**
     * PATCH /api/evenements/{id}: marks the anomaly as handled (or not) by the operator.
     *
     * @return the updated event; empty if it does not exist
     */
    Optional<Event> markHandled(String id, boolean handled, String operatorId, String comment, Instant now);

    /** Ends the anomaly (the spot is free again); false if it does not exist or is already over. */
    boolean close(String id, Instant end);
}
