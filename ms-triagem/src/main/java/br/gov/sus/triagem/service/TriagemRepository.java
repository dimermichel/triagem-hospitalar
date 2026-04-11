package br.gov.sus.triagem.service;

import br.gov.sus.triagem.model.TriagemRecord;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.Optional;
import java.util.stream.StreamSupport;

@ApplicationScoped
public class TriagemRepository {

    @Inject
    DynamoDbEnhancedClient dynamoDbEnhancedClient;

    @ConfigProperty(name = "dynamodb.table.triagem")
    String tableName;

    @ConfigProperty(name = "hospital.id")
    String hospitalId;

    private DynamoDbTable<TriagemRecord> table() {
        return dynamoDbEnhancedClient.table(tableName, TableSchema.fromBean(TriagemRecord.class));
    }

    public void salvar(TriagemRecord record) {
        table().putItem(record);
    }

    public void atualizar(TriagemRecord record) {
        table().updateItem(record);
    }

    public Optional<TriagemRecord> buscarPorId(String triagemId) {
        String pk = "HOSPITAL#" + hospitalId + "#FILA";

        return StreamSupport.stream(
                table().query(QueryConditional.keyEqualTo(
                        Key.builder().partitionValue(pk).build()
                )).spliterator(), false)
                .flatMap(page -> page.items().stream())
                .filter(r -> triagemId.equals(r.getTriagemId()))
                .findFirst();
    }

    public java.util.List<TriagemRecord> listarFilaPorHospital() {
        String pk = "HOSPITAL#" + hospitalId + "#FILA";

        return StreamSupport.stream(
                table().query(QueryConditional.keyEqualTo(
                        Key.builder().partitionValue(pk).build()
                )).spliterator(), false)
                .flatMap(page -> page.items().stream())
                .filter(r -> r.getStatus() != null &&
                             r.getStatus().name().startsWith("AGUARDANDO"))
                .toList();
    }
}
