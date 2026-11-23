package tn.supcom.smartparking.core.spots;

import java.time.Instant;
import tn.supcom.smartparking.core.model.SpotState;

/**
 * CDI event fired when the displayed state of a spot changes. The API pushes it to the clients
 * of the WebSocket {@code /api/ws/spots} (contract §1.4).
 */
public record SpotStateChanged(String spotId, SpotState state, Instant since) {
}
