package tn.supcom.smartparking.core.spots;

import java.util.List;
import tn.supcom.smartparking.core.model.Parking;
import tn.supcom.smartparking.core.model.Spot;

/**
 * Floor plan of the parking (contract §4.3, GET /api/parking): physical size, spots and forbidden zones in cm.
 * The PWA draws the SVG map from it. The guidance graph is added with the guidance (sprint 7).
 */
public record ParkingLayout(Parking parking, List<Spot> spots) {
    public ParkingLayout {
        spots = List.copyOf(spots);
    }
}
