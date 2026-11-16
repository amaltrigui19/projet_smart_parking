package tn.supcom.smartparking.erreurs;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import tn.supcom.smartparking.iam.ErreurIam;

/** Erreurs prévues par le contrat (400, 401, 403, 409…) levées par le module iam. */
@Provider
public class MapperErreurIam implements ExceptionMapper<ErreurIam> {

    @Override
    public Response toResponse(ErreurIam e) {
        return Erreurs.reponse(e.statut(), e.code(), e.getMessage());
    }
}
