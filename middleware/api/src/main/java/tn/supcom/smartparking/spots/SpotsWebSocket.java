package tn.supcom.smartparking.spots;

import jakarta.inject.Inject;
import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.List;
import tn.supcom.smartparking.iam.core.token.TokenValidator;

/**
 * WebSocket /api/ws/spots?token=&lt;jwt&gt; (contract §1.4): every spot change, in real time.
 * A browser cannot send an Authorization header on a WebSocket, hence the token in the URL.
 * The token is checked with the IAM {@link TokenValidator} (signature, kid, issuer, expiry);
 * without a valid token the connection is closed with code 1008 (policy violation).
 */
@ServerEndpoint("/ws/spots")
public class SpotsWebSocket {

    @Inject
    TokenValidator tokens;

    @Inject
    SpotBroadcaster broadcaster;

    @OnOpen
    public void open(Session session) throws IOException {
        List<String> token = session.getRequestParameterMap().get("token");
        try {
            if (token == null || token.isEmpty()) {
                throw new IllegalArgumentException("missing token");
            }
            tokens.validate(token.get(0));
        } catch (RuntimeException e) {
            session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "unauthenticated"));
            return;
        }
        broadcaster.add(session);
    }

    @OnClose
    public void close(Session session) {
        broadcaster.remove(session);
    }

    @OnError
    public void error(Session session, Throwable error) {
        broadcaster.remove(session);
    }
}
