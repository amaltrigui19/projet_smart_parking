package tn.supcom.smartparking.erreurs;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Erreurs prévues par le contrat (400, 404…) levées par le module api. */
@Provider
public class MapperErreurApi implements ExceptionMapper<ErreurApi> {

    @Override
    public Response toResponse(ErreurApi e) {
        return Erreurs.reponse(e.statut(), e.code(), e.getMessage());
    }
}
