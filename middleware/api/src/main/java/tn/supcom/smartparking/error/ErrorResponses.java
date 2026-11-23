package tn.supcom.smartparking.error;

import jakarta.json.Json;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Error response of the contract (§2.1): always {"error": "short_code", "message": "readable text"}. */
public final class ErrorResponses {

    private ErrorResponses() {
    }

    public static Response of(int status, String code, String message) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(Json.createObjectBuilder().add("error", code).add("message", message).build())
                .build();
    }
}
