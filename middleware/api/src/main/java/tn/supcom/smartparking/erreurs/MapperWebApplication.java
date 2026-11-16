package tn.supcom.smartparking.erreurs;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Erreurs produites par JAX-RS lui-même (route inconnue, méthode non permise, @RolesAllowed refusé…),
 * remises au format du contrat (§2.1).
 */
@Provider
public class MapperWebApplication implements ExceptionMapper<WebApplicationException> {

    @Context
    SecurityContext securite;

    @Override
    public Response toResponse(WebApplicationException e) {
        int statut = e.getResponse().getStatus();
        boolean anonyme = securite == null || securite.getUserPrincipal() == null;
        // Une route protégée appelée sans jeton valide : 401 (non connecté), et non 403 (mauvais rôle).
        if ((statut == 401 || statut == 403) && anonyme) {
            return Erreurs.reponse(401, "non_authentifie", "JWT absent, invalide ou expiré : reconnectez-vous.");
        }
        return switch (statut) {
            case 400 -> Erreurs.reponse(400, "requete_invalide", "Requête invalide.");
            case 403 -> Erreurs.reponse(403, "acces_refuse", "Votre rôle ne permet pas cette action.");
            case 404 -> Erreurs.reponse(404, "introuvable", "Ressource introuvable.");
            case 405 -> Erreurs.reponse(405, "methode_non_permise", "Méthode HTTP non permise sur cette route.");
            case 415 -> Erreurs.reponse(415, "type_non_supporte", "Le corps doit être en JSON (Content-Type: application/json).");
            default -> statut >= 500
                    ? Erreurs.reponse(500, "erreur_serveur", "Erreur interne du serveur.")
                    : Erreurs.reponse(statut, "requete_invalide", "Requête refusée (" + statut + ").");
        };
    }
}
