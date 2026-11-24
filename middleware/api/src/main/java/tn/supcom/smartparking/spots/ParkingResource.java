package tn.supcom.smartparking.spots;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import tn.supcom.smartparking.core.spots.SpotManager;

/** GET /api/parking (contract §2.6 and §4.3): the floor plan in cm, used by the PWA to draw the SVG map. */
@Path("/parking")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"DRIVER", "OPERATOR"})
public class ParkingResource {

    @Inject
    SpotManager spotManager;

    /** → 200 {parking, name, dimensions_cm, spots [{id, type, rect_cm, node}], forbidden_zones, graph}; 404 not_found. */
    @GET
    public JsonObject layout() {
        return SpotJson.layout(spotManager.layout());
    }
}
