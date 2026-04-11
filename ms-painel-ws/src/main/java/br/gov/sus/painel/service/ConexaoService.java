package br.gov.sus.painel.service;

import br.gov.sus.painel.model.ConexaoRecord;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.time.Instant;
import java.util.List;
import java.util.stream.StreamSupport;

@ApplicationScoped
public class ConexaoService {

    private static final Logger LOG = Logger.getLogger(ConexaoService.class);
    private static final int TTL_HORAS = 2;

    @Inject
    DynamoDbEnhancedClient dynamoClient;

    @ConfigProperty(name = "dynamodb.table.conexoes")
    String tableName;

    @ConfigProperty(name = "ws.endpoint.url")
    String wsEndpointUrl;

    private DynamoDbTable<ConexaoRecord> table() {
        return dynamoClient.table(tableName, TableSchema.fromBean(ConexaoRecord.class));
    }

    public void registrar(String connectionId, String hospitalId) {
        long agora = Instant.now().getEpochSecond();

        ConexaoRecord rec = new ConexaoRecord();
        rec.setConnectionId(connectionId);
        rec.setHospitalId(hospitalId);
        rec.setEndpoint(wsEndpointUrl);
        rec.setConectadoEm(agora);
        rec.setTtlExpiraEm(agora + (TTL_HORAS * 3600L));

        table().putItem(rec);
        LOG.debugf("Conexão registrada: %s → hospital=%s", connectionId, hospitalId);
    }

    public void remover(String connectionId) {
        table().deleteItem(Key.builder().partitionValue(connectionId).build());
    }

    public List<ConexaoRecord> listarConexoesPorHospital(String hospitalId) {
        var index = table().index("hospital-index");
        return StreamSupport.stream(
                index.query(QueryConditional.keyEqualTo(
                        Key.builder().partitionValue(hospitalId).build()
                )).spliterator(), false)
                .flatMap(page -> page.items().stream())
                .filter(c -> c.getTtlExpiraEm() > Instant.now().getEpochSecond())
                .toList();
    }
}
