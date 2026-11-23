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
 * GET and PUT /api/config/polygons (contract §2.10): the places.json calibration file (§5.2).
 * Operator only (role OPERATOR). This controller only handles HTTP; the logic lives in {@link CalibrationManager}.
 */
@Path("/config/polygons")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("OPERATOR")
public class CalibrationResource {

    @Inject
    CalibrationManager calibrationManager;

    /** → 200 with places.json; 404 not_found if nothing is stored yet. */
    @GET
    public JsonObject get() {
        return calibrationManager.getCalibration();
    }

    /** Body: the whole places.json → 200 with the stored file; 400 invalid_config; 404 without parking. */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public JsonObject save(String body) {
        return calibrationManager.saveCalibration(JsonBody.readObject(body));
    }
}
