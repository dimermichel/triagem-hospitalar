package br.gov.sus.painel.service;

import br.gov.sus.painel.model.ConexaoRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.GoneException;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.PostToConnectionRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class FanOutService {

    private static final Logger LOG = Logger.getLogger(FanOutService.class);

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ConexaoService conexaoService;

    @Inject
    DynamoDbClient dynamoDbClient;

    @ConfigProperty(name = "dynamodb.table.triagem")
    String tabelaTriagem;

    @ConfigProperty(name = "hospital.id")
    String hospitalId;

    /**
     * Envia o payload JSON para todas as conexões WebSocket ativas.
     * Remove automaticamente conexões expiradas (GoneException).
     */
    public void enviarParaConexoes(String payload, List<ConexaoRecord> conexoes) {
        if (conexoes.isEmpty()) {
            LOG.debug("Nenhuma conexão WS ativa — fan-out ignorado");
            return;
        }

        for (ConexaoRecord conexao : conexoes) {
            try {
                ApiGatewayManagementApiClient apiClient = buildClient(conexao.getEndpoint());
                apiClient.postToConnection(PostToConnectionRequest.builder()
                        .connectionId(conexao.getConnectionId())
                        .data(SdkBytes.fromString(payload, StandardCharsets.UTF_8))
                        .build());
            } catch (GoneException e) {
                // Conexão expirou ou cliente desconectou sem enviar $disconnect
                LOG.infof("Removendo conexão expirada: %s", conexao.getConnectionId());
                conexaoService.remover(conexao.getConnectionId());
            } catch (Exception e) {
                LOG.errorf(e, "Falha ao enviar para connectionId=%s", conexao.getConnectionId());
            }
        }
    }

    /**
     * Envia snapshot da fila atual para um único cliente recém-conectado.
     * Consulta o DynamoDB e monta a resposta com todos os pacientes aguardando.
     */
    public void enviarSnapshotFila(String connectionId, String hospitalId) throws Exception {
        var pk = "HOSPITAL#" + hospitalId + "#FILA";

        var result = dynamoDbClient.query(software.amazon.awssdk.services.dynamodb.model.QueryRequest.builder()
                .tableName(tabelaTriagem)
                .keyConditionExpression("pk = :pk")
                .expressionAttributeValues(Map.of(
                        ":pk", software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromS(pk)
                ))
                .build());

        var pacientes = result.items().stream()
                .filter(item -> {
                    var status = item.get("status");
                    if (status == null || status.s() == null) return false;
                    return status.s().startsWith("AGUARDANDO") || status.s().equals("EM_ATENDIMENTO");
                })
                .map(item -> Map.of(
                        "triagem_id",           safe(item, "triagem_id"),
                        "nome",                 safe(item, "nome"),
                        "idade",                safeN(item, "idade"),
                        "cor",                  safe(item, "cor_manchester"),
                        "queixa_principal",     safe(item, "queixa_principal"),
                        "prioridade_ordem",     safeN(item, "prioridade_ordem"),
                        "classificado_em",      safe(item, "classificado_em"),
                        "criado_em",            safe(item, "criado_em"),
                        "tempo_max_espera_min", safeN(item, "tempo_max_espera_min"),
                        "status",               safe(item, "status")
                ))
                .toList();

        var snapshot = Map.of(
                "tipo", "SNAPSHOT_FILA",
                "hospital_id", hospitalId,
                "pacientes", pacientes
        );

        String json = objectMapper.writeValueAsString(snapshot);

        // Envia apenas para este cliente
        var conexao = new ConexaoRecord();
        conexao.setConnectionId(connectionId);
        conexao.setEndpoint(System.getenv().getOrDefault("WS_ENDPOINT_URL", ""));
        enviarParaConexoes(json, List.of(conexao));
    }

    private ApiGatewayManagementApiClient buildClient(String endpointUrl) {
        return ApiGatewayManagementApiClient.builder()
                .endpointOverride(URI.create(endpointUrl))
                .build();
    }

    private String safe(java.util.Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> item, String key) {
        var v = item.get(key);
        return v != null && v.s() != null ? v.s() : "";
    }

    private int safeN(java.util.Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> item, String key) {
        var v = item.get(key);
        if (v == null || v.n() == null) return 0;
        return Integer.parseInt(v.n());
    }
}
