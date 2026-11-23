package tn.supcom.smartparking.spots;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.websocket.Session;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import tn.supcom.smartparking.core.spots.SpotStateChanged;

/** Keeps the open WebSocket sessions and pushes every spot change to all of them (contract §1.4). */
@ApplicationScoped
public class SpotBroadcaster {

    private static final Logger LOG = Logger.getLogger(SpotBroadcaster.class.getName());

    private final Set<Session> sessions = ConcurrentHashMap.newKeySet();

    void add(Session session) {
        sessions.add(session);
    }

    void remove(Session session) {
        sessions.remove(session);
    }

    int count() {
        return sessions.size();
    }

    /** Called by CDI when {@code SpotManager} fires a change. */
    void onChange(@Observes SpotStateChanged change) {
        String message = SpotJson.change(change);
        for (Session s : sessions) {
            if (s.isOpen()) {
                s.getAsyncRemote().sendText(message);
            } else {
                sessions.remove(s);
            }
        }
        LOG.fine(() -> "Spot change sent to " + sessions.size() + " client(s): " + message);
    }
}
