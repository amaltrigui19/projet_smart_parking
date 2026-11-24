package tn.supcom.smartparking.core.spots;

import java.time.Instant;
import tn.supcom.smartparking.core.model.SpotState;
import tn.supcom.smartparking.core.model.SpotType;

/**
 * A spot as shown to the users (contract §2.6): its state may be RESERVED (computed by the server),
 * and {@code overdue} is true when the car stayed longer than the maximum duration.
 */
public record SpotView(String id, SpotType type, SpotState state, Instant since,
                       int maxDurationMin, boolean overdue) {
}
