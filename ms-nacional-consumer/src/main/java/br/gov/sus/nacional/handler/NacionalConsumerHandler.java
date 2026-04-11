package br.gov.sus.nacional.handler;

import br.gov.sus.nacional.service.AtendimentoImportService;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.jboss.logging.Logger;

/**
 * Lambda acionada pela fila SQS nacional.
 * Recebe os consolidados diários de cada hospital e persiste no Aurora PostgreSQL.
 * Idempotente: usa INSERT ... ON CONFLICT DO NOTHING para evitar duplicatas.
 */
@Named("nacional-consumer")
public class NacionalConsumerHandler implements RequestHandler<SQSEvent, Void> {

    private static final Logger LOG = Logger.getLogger(NacionalConsumerHandler.class);

    @Inject ObjectMapper           objectMapper;
    @Inject AtendimentoImportService importService;

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        for (var msg : event.getRecords()) {
            try {
                var attrs   = msg.getMessageAttributes();
                String tipo = attrs != null && attrs.containsKey("tipo")
                              ? attrs.get("tipo").getStringValue() : "DESCONHECIDO";
                String hosp = attrs != null && attrs.containsKey("hospitalId")
                              ? attrs.get("hospitalId").getStringValue() : "?";

                LOG.infof("Processando mensagem nacional: tipo=%s, hospital=%s, msgId=%s",
                        tipo, hosp, msg.getMessageId());

                importService.importar(msg.getBody());

                LOG.infof("Importação concluída: hospital=%s", hosp);
            } catch (Exception e) {
                LOG.errorf(e, "Falha ao importar mensagem: msgId=%s", msg.getMessageId());
                throw new RuntimeException("Importação falhou — mensagem será reenviada para DLQ", e);
            }
        }
        return null;
    }
}
