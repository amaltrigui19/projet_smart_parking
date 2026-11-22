package tn.supcom.smartparking.iam.controllers;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import java.util.Locale;
import tn.supcom.smartparking.iam.api.auth.LoginRequest;
import tn.supcom.smartparking.iam.api.auth.RegisterRequest;
import tn.supcom.smartparking.iam.api.auth.TokenResponse;
import tn.supcom.smartparking.iam.api.auth.UserResponse;
import tn.supcom.smartparking.iam.exceptions.IamException;
import tn.supcom.smartparking.iam.managers.AuthenticationManager;
import tn.supcom.smartparking.iam.managers.AuthenticationManager.LoginResult;
import tn.supcom.smartparking.iam.model.User;

/**
 * Passage entre HTTP et le manager : lit les corps JSON et l'en-tête Authorization, appelle
 * {@link AuthenticationManager}, met en forme les réponses. Aucune règle métier ici.
 * Les mots de passe sont effacés de la mémoire dès la fin de l'appel.
 */
@ApplicationScoped
public class AuthController {

    private static final String BEARER = "bearer ";

    @Inject
    AuthenticationManager manager;

    public AuthController() {
        // Pour CDI
    }

    public AuthController(AuthenticationManager manager) {
        this.manager = manager;
    }

    public JsonObject register(String body) {
        JsonObject o = JsonBodies.readObject(body);
        try (RegisterRequest r = new RegisterRequest(JsonBodies.text(o, "name"), JsonBodies.text(o, "email"),
                JsonBodies.secret(o, "password"))) {
            return UserResponse.of(manager.register(r.name(), r.email(), r.password())).toJson();
        }
    }

    public JsonObject login(String body) {
        JsonObject o = JsonBodies.readObject(body);
        try (LoginRequest r = new LoginRequest(JsonBodies.text(o, "email"), JsonBodies.secret(o, "password"))) {
            LoginResult result = manager.login(r.email(), r.password());
            return new TokenResponse(result.token().accessToken(), result.token().expiresIn(),
                    UserResponse.of(result.user())).toJson();
        }
    }

    public JsonObject me(String authorizationHeader) {
        User u = manager.currentUser(bearerToken(authorizationHeader));
        return UserResponse.of(u).toMeJson();
    }

    /** Le jeton de {@code Authorization: Bearer <jwt>} ; 401 si l'en-tête est absent ou d'un autre type. */
    static String bearerToken(String header) {
        if (header == null || !header.toLowerCase(Locale.ROOT).startsWith(BEARER) || header.length() <= BEARER.length()) {
            throw IamException.unauthenticated();
        }
        return header.substring(BEARER.length()).trim();
    }
}
