package br.gov.sus.consolidador.service;

import br.gov.sus.consolidador.dto.AtendimentoDiarioDTO;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

@QuarkusTest
class ConsolidadorServiceTest {

    @InjectMock
    DynamoDbClient dynamoDbClient;

    @Inject
    ConsolidadorService service;

    @Test
    @DisplayName("consolidarDia não lança exceção com DynamoDB vazio")
    void consolidarDiaVazio() {
        QueryResponse emptyResponse = QueryResponse.builder().items(List.of()).build();
        Mockito.when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(emptyResponse);

        List<AtendimentoDiarioDTO> resultado = service.consolidarDia(LocalDate.now());

        assertThat(resultado).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("consolidarDia usa timestamp operacional conforme o status")
    void consolidarDiaFiltraPorTimestampDoStatus() {
        LocalDate data = LocalDate.of(2026, 4, 14);

        Map<String, AttributeValue> finalizadoNoDia = item(
                "triagem-1",
                "FINALIZADO",
                "2026-04-13T17:47:47.307209100Z",
                null,
                "2026-04-14T03:16:03.184057730Z"
        );

        Map<String, AttributeValue> emAtendimentoNoDia = item(
                "triagem-2",
                "EM_ATENDIMENTO",
                "2026-04-13T17:47:52.686385068Z",
                "2026-04-14T03:09:51.938338599Z",
                null
        );

        Map<String, AttributeValue> finalizadoForaDoDia = item(
                "triagem-3",
                "FINALIZADO",
                "2026-04-14T10:00:00Z",
                null,
                "2026-04-15T04:00:00Z"
        );

        Map<String, AttributeValue> statusInvalido = item(
                "triagem-4",
                "AGUARDANDO",
                "2026-04-14T10:00:00Z",
                null,
                null
        );

        QueryResponse response = QueryResponse.builder()
                .items(finalizadoNoDia, emAtendimentoNoDia, finalizadoForaDoDia, statusInvalido)
                .build();
        Mockito.when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

        List<AtendimentoDiarioDTO> resultado = service.consolidarDia(data);

        assertThat(resultado)
                .extracting(AtendimentoDiarioDTO::getTriagemId)
                .containsExactlyInAnyOrder("triagem-1", "triagem-2");
    }

    private Map<String, AttributeValue> item(String triagemId,
                                             String status,
                                             String criadoEm,
                                             String iniciandoAtendimentoEm,
                                             String finalizadoEm) {
        var item = new java.util.HashMap<String, AttributeValue>();
        item.put("triagem_id", AttributeValue.fromS(triagemId));
        item.put("status", AttributeValue.fromS(status));
        item.put("criado_em", AttributeValue.fromS(criadoEm));
        item.put("nome", AttributeValue.fromS("Paciente Teste"));
        item.put("idade", AttributeValue.fromN("30"));
        item.put("queixa_principal", AttributeValue.fromS("Dor"));
        item.put("cor_manchester", AttributeValue.fromS("AMARELO"));
        item.put("prioridade_ordem", AttributeValue.fromN("3"));
        item.put("tempo_max_espera_min", AttributeValue.fromN("60"));

        if (iniciandoAtendimentoEm != null) {
            item.put("iniciando_atendimento_em", AttributeValue.fromS(iniciandoAtendimentoEm));
        }
        if (finalizadoEm != null) {
            item.put("finalizado_em", AttributeValue.fromS(finalizadoEm));
        }

        return item;
    }
}
