package tn.supcom.smartparking.error;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Errors produced by JAX-RS itself (unknown route, method not allowed, @RolesAllowed denied…),
 * rewritten in the contract format (§2.1).
 */
@Provider
public class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {

    @Context
    SecurityContext security;

    @Override
    public Response toResponse(WebApplicationException e) {
        int status = e.getResponse().getStatus();
        boolean anonymous = security == null || security.getUserPrincipal() == null;
        // A protected route called without a valid token: 401 (not logged in), not 403 (wrong role).
        if ((status == 401 || status == 403) && anonymous) {
            return ErrorResponses.of(401, "unauthenticated", "JWT absent, invalide ou expiré : reconnectez-vous.");
        }
        return switch (status) {
            case 400 -> ErrorResponses.of(400, "invalid_request", "Requête invalide.");
            case 403 -> ErrorResponses.of(403, "forbidden", "Votre rôle ne permet pas cette action.");
            case 404 -> ErrorResponses.of(404, "not_found", "Ressource introuvable.");
            case 405 -> ErrorResponses.of(405, "method_not_allowed", "Méthode HTTP non permise sur cette route.");
            case 415 -> ErrorResponses.of(415, "unsupported_media_type", "Le corps doit être en JSON (Content-Type: application/json).");
            default -> status >= 500
                    ? ErrorResponses.of(500, "server_error", "Erreur interne du serveur.")
                    : ErrorResponses.of(status, "invalid_request", "Requête refusée (" + status + ").");
        };
    }
}
