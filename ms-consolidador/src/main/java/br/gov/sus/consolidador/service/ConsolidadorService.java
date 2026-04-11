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

        List<Map<String, AttributeValue>> items = new ArrayList<>();
        QueryResponse resp = dynamoDbClient.query(query);
        items.addAll(resp.items());

        // Paginar se houver mais resultados
        while (resp.lastEvaluatedKey() != null && !resp.lastEvaluatedKey().isEmpty()) {
            resp = dynamoDbClient.query(query.toBuilder()
                    .exclusiveStartKey(resp.lastEvaluatedKey()).build());
            items.addAll(resp.items());
        }

        // Filtra os atendimentos do dia (finalizados ou em atendimento)
        return items.stream()
                .filter(item -> pertenceAoDia(item, data))
                .filter(item -> {
                    String status = getString(item, "status");
                    return "FINALIZADO".equals(status) || "EM_ATENDIMENTO".equals(status)
                            || "ALTA".equals(status) || "TRANSFERIDO".equals(status);
                })
                .map(this::mapearAtendimento)
                .collect(Collectors.toList());
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
        String criadoEm = getString(item, "criado_em");
        if (criadoEm == null) return false;
        try {
            LocalDate dataCriacao = java.time.Instant.parse(criadoEm)
                    .atZone(BRASILIA).toLocalDate();
            return dataCriacao.equals(data);
        } catch (Exception e) {
            return false;
        }
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
