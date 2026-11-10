package tn.supcom.smartparking.sante;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GET /api/sante : page de test du déploiement (contrat, section 2.4).
 * Sprint 1 : vérifie seulement que MongoDB et le broker MQTT sont joignables
 * (connexion TCP), sans s'y authentifier.
 */
@Path("/sante")
@Produces(MediaType.APPLICATION_JSON)
public class SanteResource {

    private static final String VERSION = "0.1.0";

    @GET
    public Response sante() {
        String mongo = joignable(env("MONGO_HOTE", "mongo"), 27017);
        String mqtt = joignable(env("MQTT_HOTE", "mqtt"), 8883);
        boolean ok = "ok".equals(mongo) && "ok".equals(mqtt);

        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("statut", ok ? "ok" : "ko");
        corps.put("version", VERSION);
        corps.put("mongo", mongo);
        corps.put("mqtt", mqtt);
        corps.put("ts", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());

        return Response.status(ok ? Response.Status.OK : Response.Status.SERVICE_UNAVAILABLE)
                .entity(corps)
                .build();
    }

    private static String joignable(String hote, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(hote, port), 2000);
            return "ok";
        } catch (IOException e) {
            return "ko";
        }
    }

    private static String env(String nom, String defaut) {
        String valeur = System.getenv(nom);
        return valeur == null || valeur.isBlank() ? defaut : valeur;
    }
}
