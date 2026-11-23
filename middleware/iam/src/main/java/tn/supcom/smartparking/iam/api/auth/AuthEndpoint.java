package tn.supcom.smartparking.iam.api.auth;

import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tn.supcom.smartparking.iam.controllers.AuthController;

/**
 * Routes d'authentification (contrat en anglais), sous {@code /api} tant que l'IAM est dans api.war :
 * <ul>
 *   <li>{@code POST /api/auth/register} · public → 201 ; 400 invalid_request ; 409 email_already_exists</li>
 *   <li>{@code POST /api/auth/login} · public → 200 avec le jeton ; 401 invalid_credentials ; 403 account_disabled</li>
 *   <li>{@code GET /api/auth/me} · jeton → 200 ; 401 unauthenticated ; 403 account_disabled</li>
 * </ul>
 * L'endpoint ne fait que déclarer les routes : tout le reste est dans {@link AuthController} et les managers.
 * {@code /me} vérifie lui-même le jeton (TokenValidator, par kid) : l'IAM ne dépend pas de la configuration JWT de l'API.
 */
@Path("/auth")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class AuthEndpoint {

    @Inject
    AuthController controller;

    @POST
    @Path("/register")
    @PermitAll
    @Consumes(MediaType.APPLICATION_JSON)
    public Response register(String body) {
        return Response.status(Response.Status.CREATED).entity(controller.register(body)).build();
    }

    @POST
    @Path("/login")
    @PermitAll
    @Consumes(MediaType.APPLICATION_JSON)
    public Response login(String body) {
        return Response.ok(controller.login(body)).header("Cache-Control", "no-store").build();
    }

    @GET
    @Path("/me")
    @PermitAll
    public JsonObject me(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorization) {
        return controller.me(authorization);
    }
}
