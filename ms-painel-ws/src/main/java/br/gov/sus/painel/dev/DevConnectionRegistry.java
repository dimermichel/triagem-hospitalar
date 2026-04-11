package br.gov.sus.painel.dev;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds active WebSocket connections for dev-mode broadcasting.
 * In production the API Gateway Management API handles fan-out;
 * here we broadcast directly to in-memory session references.
 */
@ApplicationScoped
@IfBuildProfile("dev")
public class DevConnectionRegistry {

    private static final Logger LOG = Logger.getLogger(DevConnectionRegistry.class);

    private final Set<WebSocketConnection> connections =
            ConcurrentHashMap.newKeySet();

    public void register(WebSocketConnection connection) {
        connections.add(connection);
    }

    public void unregister(WebSocketConnection connection) {
        connections.remove(connection);
    }

    public void broadcast(String payload) {
        connections.removeIf(WebSocketConnection::isClosed);
        for (WebSocketConnection c : connections) {
            try {
                c.sendTextAndAwait(payload);
            } catch (Exception e) {
                LOG.warnf(e, "Falha ao enviar WS para conexão %s — removendo", c.id());
                connections.remove(c);
            }
        }
    }
}
