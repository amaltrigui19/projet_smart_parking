package tn.supcom.smartparking.erreurs;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Page d'erreur 401 (déclarée dans WEB-INF/web.xml) : quand WildFly refuse un jeton invalide, falsifié
 * ou expiré avant d'arriver à JAX-RS, la réponse garde le format du contrat (§2.1).
 */
@WebServlet("/erreurs/401")
public class ErreurNonAuthentifieServlet extends HttpServlet {

    private static final byte[] CORPS = ("{\"erreur\":\"non_authentifie\","
            + "\"message\":\"JWT absent, invalide ou expiré : reconnectez-vous.\"}").getBytes(StandardCharsets.UTF_8);

    @Override
    protected void service(HttpServletRequest requete, HttpServletResponse reponse) throws IOException {
        reponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        reponse.setContentType("application/json;charset=UTF-8");
        reponse.setContentLength(CORPS.length);
        reponse.getOutputStream().write(CORPS);
    }
}
