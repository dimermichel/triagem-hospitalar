package br.gov.sus.nacional.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class AtendimentoImportService {

    private static final Logger LOG = Logger.getLogger(AtendimentoImportService.class);

    private static final String UPSERT_ATENDIMENTO = """
            INSERT INTO atendimentos (
                triagem_id, hospital_id, data_atendimento,
                idade, queixa_principal,
                cor_manchester, prioridade_ordem, tempo_max_espera_min,
                status, criado_em, classificado_em,
                iniciado_atendimento_em, finalizado_em
            ) VALUES (?, ?, ?::date, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (triagem_id) DO NOTHING
            """;

    private static final String UPSERT_RESUMO = """
            INSERT INTO resumos_diarios (
                hospital_id, data,
                total_atendimentos, total_vermelho, total_laranja,
                total_amarelo, total_verde, total_azul
            )
            SELECT ?, ?::date,
                COUNT(*),
                COUNT(*) FILTER (WHERE cor_manchester = 'VERMELHO'),
                COUNT(*) FILTER (WHERE cor_manchester = 'LARANJA'),
                COUNT(*) FILTER (WHERE cor_manchester = 'AMARELO'),
                COUNT(*) FILTER (WHERE cor_manchester = 'VERDE'),
                COUNT(*) FILTER (WHERE cor_manchester = 'AZUL')
            FROM atendimentos
            WHERE hospital_id = ? AND data_atendimento = ?::date
            ON CONFLICT (hospital_id, data) DO UPDATE SET
                total_atendimentos = EXCLUDED.total_atendimentos,
                total_vermelho  = EXCLUDED.total_vermelho,
                total_laranja   = EXCLUDED.total_laranja,
                total_amarelo   = EXCLUDED.total_amarelo,
                total_verde     = EXCLUDED.total_verde,
                total_azul      = EXCLUDED.total_azul
            """;

    @Inject DataSource  dataSource;
    @Inject ObjectMapper objectMapper;

    @Transactional
    public void importar(String payload) throws Exception {
        JsonNode root = objectMapper.readTree(payload);
        String hospitalId = root.path("hospital_id").asText();
        String data       = root.path("data").asText();
        JsonNode lista    = root.path("atendimentos");

        if (!lista.isArray() || lista.isEmpty()) {
            LOG.warnf("Payload sem atendimentos: hospital=%s, data=%s", hospitalId, data);
            return;
        }

        LOG.infof("Importando %d atendimentos: hospital=%s, data=%s",
                lista.size(), hospitalId, data);

        try (Connection conn = dataSource.getConnection()) {
            // Garante que o hospital existe na tabela de referência
            upsertHospital(conn, hospitalId);

            // Bulk insert dos atendimentos
            int importados = inserirAtendimentos(conn, lista, hospitalId, data);
            LOG.infof("Atendimentos inseridos (novos): %d", importados);

            // Recalcula o resumo diário
            atualizarResumoDiario(conn, hospitalId, data);
        }
    }

    private void upsertHospital(Connection conn, String hospitalId) throws Exception {
        String sql = """
                INSERT INTO hospitais (id, nome) VALUES (?, ?)
                ON CONFLICT (id) DO NOTHING
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hospitalId);
            ps.setString(2, hospitalId); // nome será atualizado por cadastro separado
            ps.executeUpdate();
        }
    }

    private int inserirAtendimentos(Connection conn, JsonNode lista,
                                     String hospitalId, String data) throws Exception {
        int count = 0;
        try (PreparedStatement ps = conn.prepareStatement(UPSERT_ATENDIMENTO)) {
            for (JsonNode a : lista) {
                ps.setString(1, a.path("triagem_id").asText());
                ps.setString(2, hospitalId);
                ps.setString(3, data);
                ps.setInt   (4, a.path("idade").asInt(0));
                ps.setString(5, a.path("queixa_principal").asText());
                ps.setString(6, a.path("cor_manchester").asText());
                ps.setInt   (7, a.path("prioridade_ordem").asInt(0));
                ps.setInt   (8, a.path("tempo_max_espera_min").asInt(0));
                ps.setString(9, a.path("status").asText());
                ps.setTimestamp(10, parseTs(a.path("criado_em").asText(null)));
                ps.setTimestamp(11, parseTs(a.path("classificado_em").asText(null)));
                ps.setTimestamp(12, parseTs(a.path("iniciando_atendimento_em").asText(null)));
                ps.setTimestamp(13, parseTs(a.path("finalizado_em").asText(null)));
                ps.addBatch();
                count++;

                // Executa em lotes de 500
                if (count % 500 == 0) {
                    ps.executeBatch();
                }
            }
            ps.executeBatch();
        }
        return count;
    }

    private void atualizarResumoDiario(Connection conn, String hospitalId, String data)
            throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(UPSERT_RESUMO)) {
            ps.setString(1, hospitalId);
            ps.setString(2, data);
            ps.setString(3, hospitalId);
            ps.setString(4, data);
            ps.executeUpdate();
        }
    }

    private Timestamp parseTs(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return Timestamp.from(Instant.parse(iso));
        } catch (Exception e) {
            return null;
        }
    }
}
