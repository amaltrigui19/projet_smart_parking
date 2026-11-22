package tn.supcom.smartparking.iam.api.jwk;

import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tn.supcom.smartparking.iam.controllers.JwksController;

/**
 * {@code GET /.well-known/jwks.json} · public → 200, les clés publiques des JWT (format JWK, avec {@code kid}).
 *
 * <p>Tant que l'IAM est embarqué dans api.war, la route est servie sous {@code /api/.well-known/jwks.json} ;
 * Nginx publie {@code /.well-known/jwks.json} vers cette adresse (voir middleware/iam/README.md).
 */
@Path("/.well-known/jwks.json")
@RequestScoped
public class JwksEndpoint {

    @Inject
    JwksController controller;

    @GET
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    public Response jwks() {
        return controller.jwks();
    }
}
