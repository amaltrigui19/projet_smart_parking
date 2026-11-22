package tn.supcom.smartparking.iam.controllers;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tn.supcom.smartparking.iam.managers.JwksManager;

/** Réponse HTTP du JWKS : JSON, mis en cache 5 minutes par les clients (une rotation est vue en 5 minutes au plus). */
@ApplicationScoped
public class JwksController {

    static final String CACHE_CONTROL = "public, max-age=300";

    @Inject
    JwksManager manager;

    public JwksController() {
        // Pour CDI
    }

    public JwksController(JwksManager manager) {
        this.manager = manager;
    }

    public Response jwks() {
        return Response.ok(manager.publicJwkSetJson(), MediaType.APPLICATION_JSON_TYPE)
                .header("Cache-Control", CACHE_CONTROL)
                .build();
    }
}
