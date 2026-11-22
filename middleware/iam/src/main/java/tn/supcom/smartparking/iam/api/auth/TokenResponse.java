package tn.supcom.smartparking.iam.api.auth;

import jakarta.json.Json;
import jakarta.json.JsonObject;

/**
 * Réponse de {@code POST /api/auth/login} :
 * {@code {"access_token": "<jwt>", "type": "Bearer", "expires_in": 3600, "user": {...}}}.
 */
public record TokenResponse(String accessToken, long expiresIn, UserResponse user) {

    public static final String TYPE = "Bearer";

    public JsonObject toJson() {
        return Json.createObjectBuilder()
                .add("access_token", accessToken)
                .add("type", TYPE)
                .add("expires_in", expiresIn)
                .add("user", user.toJson())
                .build();
    }

    @Override
    public String toString() {
        return "TokenResponse[expiresIn=" + expiresIn + ", user=" + user.id() + "]";
    }
}
