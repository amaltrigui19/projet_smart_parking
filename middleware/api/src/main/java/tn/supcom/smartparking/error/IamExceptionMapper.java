package tn.supcom.smartparking.error;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import tn.supcom.smartparking.iam.ErreurIam;

/** Errors raised by the IAM module (400, 401, 403, 409…), in the contract format. */
@Provider
public class IamExceptionMapper implements ExceptionMapper<ErreurIam> {

    @Override
    public Response toResponse(ErreurIam e) {
        return ErrorResponses.of(e.statut(), e.code(), e.getMessage());
    }
}
