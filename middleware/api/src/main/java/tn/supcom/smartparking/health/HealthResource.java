package tn.supcom.smartparking.health;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
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
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * GET /api/health: deployment check (contract §2.4). Only checks that MongoDB and the MQTT broker
 * accept a TCP connection, without authenticating.
 */
@Path("/health")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {

    private static final String VERSION = "0.1.0";

    @Inject
    @ConfigProperty(name = "MONGO_HOST", defaultValue = "mongo")
    String mongoHost;

    @Inject
    @ConfigProperty(name = "MQTT_HOST", defaultValue = "mqtt")
    String mqttHost;

    @GET
    public Response health() {
        String mongo = reachable(mongoHost, 27017);
        String mqtt = reachable(mqttHost, 8883);
        boolean ok = "ok".equals(mongo) && "ok".equals(mqtt);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", ok ? "ok" : "ko");
        body.put("version", VERSION);
        body.put("mongo", mongo);
        body.put("mqtt", mqtt);
        body.put("ts", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());

        return Response.status(ok ? Response.Status.OK : Response.Status.SERVICE_UNAVAILABLE).entity(body).build();
    }

    private static String reachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 2000);
            return "ok";
        } catch (IOException e) {
            return "ko";
        }
    }
}
