package tn.supcom.smartparking.calibration;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import tn.supcom.smartparking.JsonBody;
import tn.supcom.smartparking.core.calibration.CalibrationManager;

/**
 * GET and PUT /api/config/polygones (contract §2.10): the places.json calibration file (§5.2).
 * Operator only (role OPERATOR). This controller only handles HTTP; the logic lives in {@link CalibrationManager}.
 * The route path stays as in the current contract until the English contract is agreed.
 */
@Path("/config/polygones")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("OPERATOR")
public class CalibrationResource {

    @Inject
    CalibrationManager calibrationManager;

    /** → 200 with places.json; 404 introuvable if nothing is stored yet. */
    @GET
    public JsonObject get() {
        return calibrationManager.getCalibration();
    }

    /** Body: the whole places.json → 200 with the stored file; 400 config_invalide; 404 without parking. */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public JsonObject save(String body) {
        return calibrationManager.saveCalibration(JsonBody.readObject(body));
    }
}
