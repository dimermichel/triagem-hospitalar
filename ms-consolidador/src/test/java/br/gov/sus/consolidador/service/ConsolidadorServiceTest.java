package br.gov.sus.consolidador.service;

import br.gov.sus.consolidador.dto.AtendimentoDiarioDTO;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.time.LocalDate;
import java.util.List;

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
}
