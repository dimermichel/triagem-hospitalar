package br.gov.sus.painel.dev;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

import java.util.Map;

/**
 * Dev-mode native WebSocket server at ws://localhost:8084/ws.
 * Replaces the API Gateway WebSocket bridge used in production.
 * Active only in the "dev" Quarkus build profile.
 *
 * WebSocketConnection is received as a method parameter (not @Inject field) so
 * the registry stores the concrete connection instance — safe to call from any thread.
 */
@WebSocket(path = "/ws")
@IfBuildProfile("dev")
public class DevWebSocketEndpoint {

    private static final Logger LOG = Logger.getLogger(DevWebSocketEndpoint.class);

    @Inject
    DevConnectionRegistry registry;

    @Inject
    DynamoDbClient dynamoDbClient;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "dynamodb.table.triagem")
    String tabelaTriagem;

    @ConfigProperty(name = "hospital.id")
    String hospitalId;

    @OnOpen
    void onOpen(WebSocketConnection connection) {
        LOG.infof("Cliente WS conectado: %s", connection.id());
        registry.register(connection);
        sendSnapshot(connection);
    }

    @OnTextMessage
    void onTextMessage(String message, WebSocketConnection connection) {
        try {
            var node = objectMapper.readTree(message);
            if ("GET_FILA".equals(node.path("tipo").asText(""))) {
                sendSnapshot(connection);
            }
        } catch (Exception e) {
            LOG.warnf("Mensagem WS inválida: %s", message);
        }
    }

    @OnClose
    void onClose(WebSocketConnection connection) {
        LOG.infof("Cliente WS desconectado: %s", connection.id());
        registry.unregister(connection);
    }

    private void sendSnapshot(WebSocketConnection connection) {
        try {
            var pk = "HOSPITAL#" + hospitalId + "#FILA";
            var result = dynamoDbClient.query(QueryRequest.builder()
                    .tableName(tabelaTriagem)
                    .keyConditionExpression("pk = :pk")
                    .expressionAttributeValues(Map.of(":pk", AttributeValue.fromS(pk)))
                    .build());

            var pacientes = result.items().stream()
                    .filter(item -> {
                        var s = item.get("status");
                        return s != null && s.s() != null &&
                                (s.s().startsWith("AGUARDANDO") || s.s().equals("EM_ATENDIMENTO"));
                    })
                    .map(item -> {
                        var m = new java.util.LinkedHashMap<String, Object>();
                        m.put("triagem_id",           safe(item, "triagem_id"));
                        m.put("hospital_id",          safe(item, "hospital_id"));
                        m.put("nome",                 safe(item, "nome"));
                        m.put("idade",                safeN(item, "idade"));
                        m.put("queixa_principal",     safe(item, "queixa_principal"));
                        m.put("cor",                  safe(item, "cor_manchester"));
                        m.put("status",               safe(item, "status"));
                        m.put("prioridade_ordem",     safeN(item, "prioridade_ordem"));
                        m.put("classificado_em",      safe(item, "classificado_em"));
                        m.put("criado_em",            safe(item, "criado_em"));
                        m.put("tempo_max_espera_min", safeN(item, "tempo_max_espera_min"));
                        return m;
                    })
                    .toList();

            var snapshot = Map.of(
                    "tipo",        "SNAPSHOT_FILA",
                    "hospital_id", hospitalId,
                    "pacientes",   pacientes
            );
            connection.sendTextAndAwait(objectMapper.writeValueAsString(snapshot));
        } catch (Exception e) {
            LOG.errorf(e, "Falha ao enviar snapshot para %s", connection.id());
        }
    }

    private String safe(Map<String, AttributeValue> item, String key) {
        var v = item.get(key);
        return v != null && v.s() != null ? v.s() : "";
    }

    private int safeN(Map<String, AttributeValue> item, String key) {
        var v = item.get(key);
        if (v == null || v.n() == null) return 0;
        return Integer.parseInt(v.n());
    }
}
