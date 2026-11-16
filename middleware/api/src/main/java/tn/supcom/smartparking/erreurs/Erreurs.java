package tn.supcom.smartparking.erreurs;

import jakarta.json.Json;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Réponse d'erreur du contrat (§2.1) : toujours {"erreur": "code_court", "message": "texte lisible"}. */
public final class Erreurs {

    private Erreurs() {
    }

    public static Response reponse(int statut, String code, String message) {
        return Response.status(statut)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(Json.createObjectBuilder().add("erreur", code).add("message", message).build())
                .build();
    }
}
