package tn.supcom.smartparking.iam.api.error;

import jakarta.json.Json;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import tn.supcom.smartparking.iam.exceptions.IamException;

/** Erreurs de l'IAM au format du contrat en anglais : {@code {"error": "code", "message": "texte en français"}}. */
@Provider
public class IamExceptionMapper implements ExceptionMapper<IamException> {

    @Override
    public Response toResponse(IamException e) {
        Response.ResponseBuilder r = Response.status(e.status())
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(Json.createObjectBuilder().add("error", e.code()).add("message", e.getMessage()).build());
        if (e.status() == 401) {
            r.header("WWW-Authenticate", "Bearer");
        }
        return r.build();
    }
}
