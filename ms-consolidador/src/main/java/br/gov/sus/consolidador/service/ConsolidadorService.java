package br.gov.sus.consolidador.service;

import br.gov.sus.consolidador.dto.AtendimentoDiarioDTO;
import br.gov.sus.consolidador.dto.ResumoHospitalDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class ConsolidadorService {

    private static final Logger LOG = Logger.getLogger(ConsolidadorService.class);
    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final String STATUS = "status";
    private static final String STATUS_EM_ATENDIMENTO = "EM_ATENDIMENTO";
    private static final String STATUS_FINALIZADO = "FINALIZADO";
    private static final String STATUS_ALTA = "ALTA";
    private static final String STATUS_TRANSFERIDO = "TRANSFERIDO";

    @Inject
    DynamoDbClient dynamoDbClient;

    @Inject
    SqsClient sqsClient;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "dynamodb.table.triagem")
    String tableName;

    @ConfigProperty(name = "hospital.id")
    String hospitalId;

    @ConfigProperty(name = "sqs.queue.nacional.url")
    String nacionalQueueUrl;

    public List<AtendimentoDiarioDTO> consolidarDia(LocalDate data) {
        String pk = "HOSPITAL#" + hospitalId + "#FILA";

        // Consulta todos os registros do hospital na tabela
        QueryRequest query = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("pk = :pk")
                .expressionAttributeValues(Map.of(":pk", AttributeValue.fromS(pk)))
                .build();

        LOG.infof("Consulta de atendimentos para data: %s", data);
        LOG.infof("Consulta: %s", query);

        List<Map<String, AttributeValue>> items = new ArrayList<>();
        QueryResponse resp = dynamoDbClient.query(query);
        items.addAll(resp.items());

        LOG.info(items);
        LOG.infof("Total de registros: %d", items.size());
        LOG.infof("Consulta concluída com sucesso");

        // Paginar se houver mais resultados
        while (resp.lastEvaluatedKey() != null && !resp.lastEvaluatedKey().isEmpty()) {
            resp = dynamoDbClient.query(query.toBuilder()
                    .exclusiveStartKey(resp.lastEvaluatedKey()).build());
            items.addAll(resp.items());
        }

        // Filtra os atendimentos do dia usando a data operacional correta por status
        var filteredItems = items.stream()
                .filter(this::statusValido)
                .filter(item -> pertenceAoDia(item, data))
                .map(this::mapearAtendimento)
                .toList();

        LOG.info(filteredItems);
        LOG.infof("Total de registros filtrados: %d", filteredItems.size());
        return filteredItems;
    }

    public void publicarNacional(LocalDate data, List<AtendimentoDiarioDTO> atendimentos) throws Exception {
        // Compila resumo por cor
        Map<String, Long> porCor = atendimentos.stream()
                .collect(Collectors.groupingBy(AtendimentoDiarioDTO::getCorManchester, Collectors.counting()));

        ResumoHospitalDTO resumo = new ResumoHospitalDTO();
        resumo.setHospitalId(hospitalId);
        resumo.setData(data.toString());
        resumo.setTotalAtendimentos(atendimentos.size());
        resumo.setAtendimentosPorCor(porCor);
        resumo.setAtendimentos(atendimentos);
        resumo.setPublicadoEm(java.time.Instant.now().toString());

        String payload = objectMapper.writeValueAsString(resumo);

        // Quebra em batches de 250KB para não ultrapassar limite do SQS (256KB)
        if (payload.length() > 200_000) {
            publicarEmLotes(atendimentos, data, porCor);
        } else {
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(nacionalQueueUrl)
                    .messageBody(payload)
                    .messageAttributes(Map.of(
                            "hospitalId", strAttr(hospitalId),
                            "data", strAttr(data.toString()),
                            "tipo", strAttr("CONSOLIDADO_DIARIO")
                    ))
                    .build());
        }
    }

    private void publicarEmLotes(List<AtendimentoDiarioDTO> atendimentos, LocalDate data,
                                  Map<String, Long> porCor) throws Exception {
        int tamanhoLote = 100;
        List<List<AtendimentoDiarioDTO>> lotes = new ArrayList<>();
        for (int i = 0; i < atendimentos.size(); i += tamanhoLote) {
            lotes.add(atendimentos.subList(i, Math.min(i + tamanhoLote, atendimentos.size())));
        }

        for (int i = 0; i < lotes.size(); i++) {
            ResumoHospitalDTO lote = new ResumoHospitalDTO();
            lote.setHospitalId(hospitalId);
            lote.setData(data.toString());
            lote.setTotalAtendimentos(atendimentos.size());
            lote.setAtendimentosPorCor(porCor);
            lote.setAtendimentos(lotes.get(i));
            lote.setLoteNumero(i + 1);
            lote.setLoteTotal(lotes.size());
            lote.setPublicadoEm(java.time.Instant.now().toString());

            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(nacionalQueueUrl)
                    .messageBody(objectMapper.writeValueAsString(lote))
                    .messageAttributes(Map.of(
                            "hospitalId", strAttr(hospitalId),
                            "data", strAttr(data.toString()),
                            "tipo", strAttr("CONSOLIDADO_DIARIO_LOTE")
                    ))
                    .build());
        }
        LOG.infof("Publicado em %d lotes para o banco nacional", lotes.size());
    }

    private boolean pertenceAoDia(Map<String, AttributeValue> item, LocalDate data) {
        String timestampReferencia = getTimestampReferencia(item);
        if (timestampReferencia == null) return false;
        try {
            LocalDate dataCriacao = java.time.Instant.parse(timestampReferencia)
                    .atZone(BRASILIA).toLocalDate();
            return dataCriacao.equals(data);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean statusValido(Map<String, AttributeValue> item) {
        String status = getString(item, STATUS);
        return STATUS_FINALIZADO.equals(status) || STATUS_EM_ATENDIMENTO.equals(status)
                || STATUS_ALTA.equals(status) || STATUS_TRANSFERIDO.equals(status);
    }

    private String getTimestampReferencia(Map<String, AttributeValue> item) {
        String status = getString(item, STATUS);
        if (status == null) {
            return null;
        }

        return switch (status) {
            case STATUS_EM_ATENDIMENTO -> getString(item, "iniciando_atendimento_em");
            case STATUS_FINALIZADO, STATUS_ALTA, STATUS_TRANSFERIDO -> getString(item, "finalizado_em");
            default -> null;
        };
    }

    private AtendimentoDiarioDTO mapearAtendimento(Map<String, AttributeValue> item) {
        AtendimentoDiarioDTO dto = new AtendimentoDiarioDTO();
        dto.setTriagemId(getString(item, "triagem_id"));
        dto.setHospitalId(hospitalId);
        dto.setNome(getString(item, "nome"));
        dto.setIdade(getInt(item, "idade"));
        dto.setQueixaPrincipal(getString(item, "queixa_principal"));
        dto.setCorManchester(getString(item, "cor_manchester"));
        dto.setPrioridadeOrdem(getInt(item, "prioridade_ordem"));
        dto.setTempoMaxEsperaMin(getInt(item, "tempo_max_espera_min"));
        dto.setStatus(getString(item, "status"));
        dto.setCriadoEm(getString(item, "criado_em"));
        dto.setClassificadoEm(getString(item, "classificado_em"));
        dto.setIniciandoAtendimentoEm(getString(item, "iniciando_atendimento_em"));
        dto.setFinalizadoEm(getString(item, "finalizado_em"));
        return dto;
    }

    private String getString(Map<String, AttributeValue> item, String key) {
        AttributeValue v = item.get(key);
        return v != null ? v.s() : null;
    }

    private int getInt(Map<String, AttributeValue> item, String key) {
        AttributeValue v = item.get(key);
        if (v == null || v.n() == null) return 0;
        return Integer.parseInt(v.n());
    }

    private software.amazon.awssdk.services.sqs.model.MessageAttributeValue strAttr(String val) {
        return software.amazon.awssdk.services.sqs.model.MessageAttributeValue.builder()
                .dataType("String").stringValue(val).build();
    }
}
