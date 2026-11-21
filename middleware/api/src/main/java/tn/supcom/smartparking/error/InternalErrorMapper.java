package tn.supcom.smartparking.error;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Any other exception: 500 erreur_serveur. Details go to the server log, never to the response (contract §2.1). */
@Provider
public class InternalErrorMapper implements ExceptionMapper<RuntimeException> {

    private static final Logger LOG = Logger.getLogger(InternalErrorMapper.class.getName());

    @Override
    public Response toResponse(RuntimeException e) {
        LOG.log(Level.SEVERE, "Internal API error", e);
        return ErrorResponses.of(500, "erreur_serveur", "Erreur interne du serveur.");
    }
}
