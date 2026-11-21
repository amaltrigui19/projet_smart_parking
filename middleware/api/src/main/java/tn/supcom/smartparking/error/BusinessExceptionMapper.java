package tn.supcom.smartparking.error;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import tn.supcom.smartparking.core.exception.BusinessException;
import tn.supcom.smartparking.core.exception.NotFoundException;

/** Maps the business errors of core to HTTP responses in the contract format. */
@Provider
public class BusinessExceptionMapper implements ExceptionMapper<BusinessException> {

    @Override
    public Response toResponse(BusinessException e) {
        int status = e instanceof NotFoundException ? 404 : 400;
        return ErrorResponses.of(status, e.code(), e.getMessage());
    }
}
