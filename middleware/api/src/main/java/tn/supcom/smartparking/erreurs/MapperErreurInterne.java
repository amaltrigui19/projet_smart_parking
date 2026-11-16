package tn.supcom.smartparking.erreurs;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Toute autre exception : 500 erreur_serveur. Le détail va dans les journaux du serveur,
 * jamais dans la réponse (contrat §2.1).
 */
@Provider
public class MapperErreurInterne implements ExceptionMapper<RuntimeException> {

    private static final Logger JOURNAL = Logger.getLogger(MapperErreurInterne.class.getName());

    @Override
    public Response toResponse(RuntimeException e) {
        JOURNAL.log(Level.SEVERE, "Erreur interne de l'API", e);
        return Erreurs.reponse(500, "erreur_serveur", "Erreur interne du serveur.");
    }
}
