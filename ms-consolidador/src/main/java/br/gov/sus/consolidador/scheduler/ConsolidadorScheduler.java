package br.gov.sus.consolidador.scheduler;

import br.gov.sus.consolidador.dto.AtendimentoDiarioDTO;
import br.gov.sus.consolidador.service.ConsolidadorService;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Lambda acionada pelo EventBridge (cron: 23h55 horário de Brasília).
 * Consolida todos os atendimentos do dia e publica na fila nacional.
 */
@Named("consolidador")
public class ConsolidadorScheduler implements RequestHandler<Map<String, Object>, Void> {

    private static final Logger LOG = Logger.getLogger(ConsolidadorScheduler.class);

    @Inject
    ConsolidadorService consolidadorService;

    @Override
    public Void handleRequest(Map<String, Object> event, Context context) {
        LocalDate data = LocalDate.now();
        LOG.infof("Iniciando consolidação diária: data=%s", data);

        try {
            List<AtendimentoDiarioDTO> atendimentos = consolidadorService.consolidarDia(data);
            consolidadorService.publicarNacional(data, atendimentos);

            LOG.infof("Consolidação concluída: data=%s, total=%d atendimentos", data, atendimentos.size());
        } catch (Exception e) {
            LOG.errorf(e, "Falha na consolidação diária: data=%s", data);
            throw new RuntimeException("Consolidação falhou", e);
        }

        return null;
    }
}
