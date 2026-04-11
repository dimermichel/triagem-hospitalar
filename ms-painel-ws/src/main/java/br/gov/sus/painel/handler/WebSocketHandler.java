package br.gov.sus.painel.handler;

import br.gov.sus.painel.service.ConexaoService;
import br.gov.sus.painel.service.FanOutService;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.jboss.logging.Logger;

/**
 * Handler principal do WebSocket.
 * Roteado pelo API Gateway conforme o campo routeKey:
 *   $connect    → registra connectionId no DynamoDB
 *   $disconnect → remove connectionId
 *   $default    → processa mensagem do cliente (ex: GET_FILA)
 */
@Named("ws-handler")
public class WebSocketHandler
        implements RequestHandler<APIGatewayV2WebSocketEvent, APIGatewayV2WebSocketResponse> {

    private static final Logger LOG = Logger.getLogger(WebSocketHandler.class);

    @Inject ConexaoService conexaoService;
    @Inject FanOutService  fanOutService;
    @Inject ObjectMapper   objectMapper;

    @Override
    public APIGatewayV2WebSocketResponse handleRequest(
            APIGatewayV2WebSocketEvent event, Context context) {

        String connectionId = event.getRequestContext().getConnectionId();
        String routeKey     = event.getRequestContext().getRouteKey();
        String hospitalId   = extrairHospitalId(event);

        LOG.debugf("WS evento: route=%s, connectionId=%s, hospital=%s",
                routeKey, connectionId, hospitalId);

        return switch (routeKey) {
            case "$connect"    -> handleConnect(connectionId, hospitalId);
            case "$disconnect" -> handleDisconnect(connectionId);
            default            -> handleDefault(event, connectionId, hospitalId);
        };
    }

    // ─── $connect ────────────────────────────────────────────────────────────

    private APIGatewayV2WebSocketResponse handleConnect(String connectionId, String hospitalId) {
        try {
            conexaoService.registrar(connectionId, hospitalId);
            LOG.infof("Cliente conectado: connectionId=%s, hospital=%s", connectionId, hospitalId);
            return ok();
        } catch (Exception e) {
            LOG.errorf(e, "Falha ao registrar conexão: %s", connectionId);
            return error(500);
        }
    }

    // ─── $disconnect ─────────────────────────────────────────────────────────

    private APIGatewayV2WebSocketResponse handleDisconnect(String connectionId) {
        try {
            conexaoService.remover(connectionId);
            LOG.infof("Cliente desconectado: connectionId=%s", connectionId);
            return ok();
        } catch (Exception e) {
            LOG.warnf("Falha ao remover conexão (pode já ter expirado): %s", connectionId);
            return ok(); // não falhar no disconnect
        }
    }

    // ─── $default — mensagens do cliente ─────────────────────────────────────

    private APIGatewayV2WebSocketResponse handleDefault(
            APIGatewayV2WebSocketEvent event, String connectionId, String hospitalId) {
        try {
            JsonNode msg = objectMapper.readTree(event.getBody());
            String tipo  = msg.path("tipo").asText("");

            if ("GET_FILA".equals(tipo)) {
                // Envia snapshot da fila atual para o cliente recém-conectado
                fanOutService.enviarSnapshotFila(connectionId, hospitalId);
            } else {
                LOG.warnf("Tipo de mensagem desconhecido: %s", tipo);
            }
        } catch (Exception e) {
            LOG.errorf(e, "Erro ao processar mensagem WS do cliente: %s", connectionId);
        }
        return ok();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** Extrai hospital_id dos query params da URL de conexão */
    private String extrairHospitalId(APIGatewayV2WebSocketEvent event) {
        var params = event.getQueryStringParameters();
        if (params != null && params.containsKey("hospital_id")) {
            return params.get("hospital_id");
        }
        // fallback: variável de ambiente da Lambda
        return System.getenv().getOrDefault("HOSPITAL_ID", "desconhecido");
    }

    private APIGatewayV2WebSocketResponse ok() {
        var r = new APIGatewayV2WebSocketResponse();
        r.setStatusCode(200);
        return r;
    }

    private APIGatewayV2WebSocketResponse error(int code) {
        var r = new APIGatewayV2WebSocketResponse();
        r.setStatusCode(code);
        return r;
    }
}
