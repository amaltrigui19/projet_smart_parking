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
            return ErrorResponses.of(401, "non_authentifie", "JWT absent, invalide ou expiré : reconnectez-vous.");
        }
        return switch (status) {
            case 400 -> ErrorResponses.of(400, "requete_invalide", "Requête invalide.");
            case 403 -> ErrorResponses.of(403, "acces_refuse", "Votre rôle ne permet pas cette action.");
            case 404 -> ErrorResponses.of(404, "introuvable", "Ressource introuvable.");
            case 405 -> ErrorResponses.of(405, "methode_non_permise", "Méthode HTTP non permise sur cette route.");
            case 415 -> ErrorResponses.of(415, "type_non_supporte", "Le corps doit être en JSON (Content-Type: application/json).");
            default -> status >= 500
                    ? ErrorResponses.of(500, "erreur_serveur", "Erreur interne du serveur.")
                    : ErrorResponses.of(status, "requete_invalide", "Requête refusée (" + status + ").");
        };
    }
}
