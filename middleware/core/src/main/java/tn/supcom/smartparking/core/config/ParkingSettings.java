package tn.supcom.smartparking.core.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Parking settings read through MicroProfile Config instead of being hard-coded.
 * Each property can be overridden by an environment variable (e.g. SMARTPARKING_PARKING_ID).
 */
@ApplicationScoped
public class ParkingSettings {

    @Inject
    @ConfigProperty(name = "smartparking.parking.id", defaultValue = "P1")
    String parkingId;

    /** Spot ids of the floor plan (contract §4.3). Will come from GET /api/config/plan later. */
    @Inject
    @ConfigProperty(name = "smartparking.parking.spot-ids", defaultValue = "A1,A2,A3,A4,B1,B2,B3,B4")
    List<String> planSpotIds;

    public String parkingId() {
        return parkingId;
    }

    public List<String> planSpotIds() {
        return planSpotIds;
    }
}
