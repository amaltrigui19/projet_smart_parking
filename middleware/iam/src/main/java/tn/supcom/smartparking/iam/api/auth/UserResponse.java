package tn.supcom.smartparking.iam.api.auth;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import tn.supcom.smartparking.iam.model.User;

/** Un compte tel que renvoyé par l'API : jamais l'empreinte du mot de passe. */
public record UserResponse(String id, String name, String email, String role, boolean active) {

    public static UserResponse of(User u) {
        return new UserResponse(u.id(), u.name(), u.email(), u.role().name(), u.active());
    }

    JsonObjectBuilder builder() {
        return Json.createObjectBuilder()
                .add("id", id)
                .add("name", name)
                .add("email", email)
                .add("role", role)
                .add("active", active);
    }

    /** Réponse de register : {@code {id, name, email, role, active}}. */
    public JsonObject toJson() {
        return builder().build();
    }

    /** Réponse de me : les mêmes champs, plus {@code permissions} (collection prévue plus tard : liste vide). */
    public JsonObject toMeJson() {
        return builder().add("permissions", Json.createArrayBuilder()).build();
    }
}
