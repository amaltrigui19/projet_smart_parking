package tn.supcom.smartparking.spots;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;
import java.time.Instant;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tn.supcom.smartparking.JsonBody;
import tn.supcom.smartparking.core.config.ParkingSettings;
import tn.supcom.smartparking.core.exception.InvalidRequestException;
import tn.supcom.smartparking.core.exception.NotFoundException;
import tn.supcom.smartparking.core.model.SpotState;
import tn.supcom.smartparking.core.spots.SpotManager;

/**
 * GET /api/spots and GET /api/spots/{id} (contract §2.6): the map of the parking, for every logged-in user.
 * This controller only handles HTTP; the logic lives in {@link SpotManager}.
 */
@Path("/spots")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"DRIVER", "OPERATOR"})
public class SpotResource {

    @Inject
    SpotManager spotManager;

    @Inject
    ParkingSettings settings;

    /** Simulation route, only for the demo and the tests until the MQTT reception (sprint 5). */
    @Inject
    @ConfigProperty(name = "smartparking.simulation", defaultValue = "false")
    boolean simulation;

    @Context
    SecurityContext security;

    /** → 200 {parking, ts, summary, spots}. */
    @GET
    public JsonObject list() {
        return SpotJson.list(settings.parkingId(), spotManager.list(), Instant.now());
    }

    /** → 200 with the spot, overdue and, for the operator, lock; 404 not_found. */
    @GET
    @Path("/{id}")
    public JsonObject get(@PathParam("id") String id) {
        return SpotJson.detail(spotManager.get(id), security.isUserInRole("OPERATOR"));
    }

    /**
     * SIMULATION ONLY (SMARTPARKING_SIMULATION=true, operator): plays the role of the Pi.
     * Body {"state": "occupied"} → 200 with the spot, and the change is pushed on the WebSocket.
     * Without the variable, the route answers 404 as if it did not exist. Replaced by MQTT at sprint 5.
     */
    @PUT
    @Path("/{id}/state")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("OPERATOR")
    public JsonObject simulateState(@PathParam("id") String id, String body) {
        if (!simulation) {
            throw new NotFoundException("Ressource introuvable.");
        }
        SpotState state;
        try {
            state = SpotState.fromValue(JsonBody.readObject(body).getString("state", ""));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("'state' doit être free ou occupied.");
        }
        return SpotJson.detail(spotManager.updateState(id, state, null), true);
    }
}
