package br.gov.sus.manchester.service;

import br.gov.sus.manchester.dto.TriagemPayloadDTO;
import br.gov.sus.manchester.engine.ManchesterEngine;
import br.gov.sus.manchester.model.CorManchester;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.jboss.logging.Logger;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.Instant;
import java.util.Map;

/**
 * Lambda acionada por SQS.
 * Lê o payload da triagem, classifica com o ManchesterEngine,
 * atualiza o DynamoDB e publica evento no SQS do painel (WebSocket fan-out).
 */
@Named("classificacao")
public class ClassificacaoLambdaHandler implements RequestHandler<SQSEvent, Void> {

    private static final Logger LOG = Logger.getLogger(ClassificacaoLambdaHandler.class);

    @Inject
    ManchesterEngine engine;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    DynamoDbClient dynamoDbClient;

    @Inject
    SqsClient sqsClient;

    @org.eclipse.microprofile.config.inject.ConfigProperty(name = "dynamodb.table.triagem")
    String tableName;

    @org.eclipse.microprofile.config.inject.ConfigProperty(name = "hospital.id")
    String hospitalId;

    @org.eclipse.microprofile.config.inject.ConfigProperty(name = "sqs.queue.painel.url")
    String painelQueueUrl;

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        for (SQSEvent.SQSMessage msg : event.getRecords()) {
            try {
                processar(msg);
            } catch (Exception e) {
                LOG.errorf(e, "Erro ao processar mensagem SQS: messageId=%s", msg.getMessageId());
                // Não re-lança para evitar que toda a batch seja retentada;
                // mensagem irá para DLQ após maxReceiveCount
            }
        }
        return null;
    }

    private void processar(SQSEvent.SQSMessage msg) throws Exception {
        TriagemPayloadDTO payload = objectMapper.readValue(msg.getBody(), TriagemPayloadDTO.class);

        LOG.infof("Classificando triagem: id=%s, queixa='%s'",
                payload.getTriagemId(), payload.getQueixaPrincipal());

        CorManchester cor = engine.classificar(payload);
        Instant agora = Instant.now();

        // Atualiza o registro no DynamoDB com cor, prioridade e status.
        // A condição garante que não sobrescrevemos um status já avançado (ex: EM_ATENDIMENTO).
        try {
            atualizarDynamoDB(payload, cor, agora);
        } catch (ConditionalCheckFailedException e) {
            LOG.warnf("Triagem %s já avançou de status antes da classificação — mantendo status atual",
                    payload.getTriagemId());
        }

        // Publica no SQS do painel para fan-out via WebSocket
        publicarEventoPainel(payload, cor, agora);

        LOG.infof("Triagem classificada: id=%s → %s %s (espera máx: %d min)",
                payload.getTriagemId(), cor.getEmoji(), cor.name(), cor.getTempoMaxEsperaMin());
    }

    private void atualizarDynamoDB(TriagemPayloadDTO payload, CorManchester cor, Instant agora)
            throws ConditionalCheckFailedException {
        String pk = "HOSPITAL#" + payload.getHospitalId() + "#FILA";
        // Reconstrói o SK original (como foi criado em ms-triagem antes da classificação)
        String skNovo = "0#null#" + payload.getCriadoEm().toEpochMilli()
                + "#" + payload.getTriagemId();

        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(
                        "pk", AttributeValue.fromS(pk),
                        "sk", AttributeValue.fromS(skNovo)
                ))
                .updateExpression("SET cor_manchester = :cor, prioridade_ordem = :po, "
                        + "tempo_max_espera_min = :tme, #st = :st, classificado_em = :ce")
                .conditionExpression("#st = :expectedSt")
                .expressionAttributeNames(Map.of("#st", "status"))
                .expressionAttributeValues(Map.of(
                        ":cor",        AttributeValue.fromS(cor.name()),
                        ":po",         AttributeValue.fromN(String.valueOf(cor.getPrioridadeOrdem())),
                        ":tme",        AttributeValue.fromN(String.valueOf(cor.getTempoMaxEsperaMin())),
                        ":st",         AttributeValue.fromS("AGUARDANDO_ATENDIMENTO"),
                        ":ce",         AttributeValue.fromS(agora.toString()),
                        ":expectedSt", AttributeValue.fromS("AGUARDANDO_CLASSIFICACAO")
                ))
                .build());
    }

    private void publicarEventoPainel(TriagemPayloadDTO payload, CorManchester cor, Instant agora) throws Exception {
        var paciente = new java.util.LinkedHashMap<String, Object>();
        paciente.put("triagem_id",           payload.getTriagemId());
        paciente.put("hospital_id",          payload.getHospitalId());
        paciente.put("nome",                 payload.getNome());
        paciente.put("idade",                payload.getIdade());
        paciente.put("queixa_principal",     payload.getQueixaPrincipal());
        paciente.put("cor",                  cor.name());
        paciente.put("emoji",                cor.getEmoji());
        paciente.put("descricao_cor",        cor.getDescricao());
        paciente.put("tempo_max_espera_min", cor.getTempoMaxEsperaMin());
        paciente.put("prioridade_ordem",     cor.getPrioridadeOrdem());
        paciente.put("status",               "AGUARDANDO_ATENDIMENTO");
        paciente.put("classificado_em",      agora.toString());
        paciente.put("criado_em",            payload.getCriadoEm().toString());

        var evento = new java.util.LinkedHashMap<String, Object>();
        evento.put("tipo",     "NOVO_PACIENTE");
        evento.put("paciente", paciente);

        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(painelQueueUrl)
                .messageBody(objectMapper.writeValueAsString(evento))
                .build());
    }
}
