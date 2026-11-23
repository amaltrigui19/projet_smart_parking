package tn.supcom.smartparking.error;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 401 error page (declared in WEB-INF/web.xml): when WildFly rejects an invalid, forged or expired token
 * before JAX-RS, the response still uses the contract format (§2.1).
 */
@WebServlet("/errors/401")
public class UnauthenticatedErrorServlet extends HttpServlet {

    private static final byte[] BODY = ("{\"error\":\"unauthenticated\","
            + "\"message\":\"JWT absent, invalide ou expiré : reconnectez-vous.\"}").getBytes(StandardCharsets.UTF_8);

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.setContentLength(BODY.length);
        response.getOutputStream().write(BODY);
    }
}
