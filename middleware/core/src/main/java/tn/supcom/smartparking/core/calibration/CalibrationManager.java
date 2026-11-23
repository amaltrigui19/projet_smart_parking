package tn.supcom.smartparking.core.calibration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import java.util.List;
import tn.supcom.smartparking.core.config.ParkingSettings;
import tn.supcom.smartparking.core.exception.InvalidConfigException;
import tn.supcom.smartparking.core.exception.NotFoundException;
import tn.supcom.smartparking.core.model.Parking;
import tn.supcom.smartparking.core.model.Spot;
import tn.supcom.smartparking.core.persistence.ParkingRepository;
import tn.supcom.smartparking.core.persistence.SpotRepository;

/**
 * Business logic of the camera calibration (places.json, contract §2.10 and §5.2).
 * The REST resource only parses the request and delegates here.
 * Spots go to the spot repository (the state reported by the Pi is kept);
 * resolution and forbidden zones go to the parking document.
 */
@ApplicationScoped
public class CalibrationManager {

    @Inject
    SpotRepository spotRepository;

    @Inject
    ParkingRepository parkingRepository;

    @Inject
    ParkingSettings settings;

    /** Current calibration file. */
    public JsonObject getCalibration() {
        String parkingId = settings.parkingId();
        Parking parking = parkingRepository.findById(parkingId)
                .orElseThrow(() -> new NotFoundException(
                        "Aucun fichier de polygones enregistré : le parking " + parkingId + " n'existe pas encore."));
        List<Spot> spots = spotRepository.findByParking(parking.id());
        if (spots.isEmpty() && parking.forbiddenZones().isEmpty()) {
            throw new NotFoundException("Aucun fichier de polygones enregistré pour l'instant.");
        }
        return CalibrationMapper.toFile(parking, spots);
    }

    /** Validates and stores a whole calibration file, then returns it as saved. */
    public JsonObject saveCalibration(JsonObject file) {
        String parkingId = settings.parkingId();
        List<String> errors = CalibrationValidator.validate(file, parkingId, settings.planSpotIds());
        if (!errors.isEmpty()) {
            throw new InvalidConfigException(errors);
        }
        Parking existing = parkingRepository.findById(parkingId)
                .orElseThrow(() -> new NotFoundException("Le parking " + parkingId + " n'existe pas encore : lancer les "
                        + "données d'exemple (server/mongo/seed.js, voir middleware/README.md)."));
        spotRepository.replacePolygons(existing.id(), CalibrationMapper.spots(file, existing.id()));
        parkingRepository.save(CalibrationMapper.parking(file, existing));
        // Next sprint: publish the file to the Pi on parking/<id>/config (contract §5.3).
        return getCalibration();
    }
}
