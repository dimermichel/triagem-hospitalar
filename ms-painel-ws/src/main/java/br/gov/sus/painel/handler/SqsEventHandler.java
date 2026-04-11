package br.gov.sus.painel.handler;

import br.gov.sus.painel.service.ConexaoService;
import br.gov.sus.painel.service.FanOutService;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.jboss.logging.Logger;

/**
 * Lambda acionada pela fila SQS do painel.
 * Cada mensagem publicada pelo ms-manchester é entregue aqui,
 * que então faz fan-out para todos os clientes WebSocket conectados do hospital.
 */
@Named("sqs-fanout")
public class SqsEventHandler implements RequestHandler<SQSEvent, Void> {

    private static final Logger LOG = Logger.getLogger(SqsEventHandler.class);

    @Inject ConexaoService conexaoService;
    @Inject FanOutService  fanOutService;

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        for (SQSEvent.SQSMessage msg : event.getRecords()) {
            try {
                String hospitalId = extrairHospitalId(msg);
                LOG.debugf("Fan-out SQS: messageId=%s, hospital=%s", msg.getMessageId(), hospitalId);

                var conexoes = conexaoService.listarConexoesPorHospital(hospitalId);
                LOG.infof("Enviando para %d conexões WS ativas: hospital=%s", conexoes.size(), hospitalId);

                fanOutService.enviarParaConexoes(msg.getBody(), conexoes);
            } catch (Exception e) {
                LOG.errorf(e, "Erro no fan-out: messageId=%s", msg.getMessageId());
                // Não re-lança — mensagem vai para DLQ após maxReceiveCount
            }
        }
        return null;
    }

    private String extrairHospitalId(SQSEvent.SQSMessage msg) {
        var attrs = msg.getMessageAttributes();
        if (attrs != null && attrs.containsKey("hospitalId")) {
            return attrs.get("hospitalId").getStringValue();
        }
        // fallback: variável de ambiente
        return System.getenv().getOrDefault("HOSPITAL_ID", "desconhecido");
    }
}
