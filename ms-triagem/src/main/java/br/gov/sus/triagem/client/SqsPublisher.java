package br.gov.sus.triagem.client;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.Map;

@ApplicationScoped
public class SqsPublisher {

    private static final Logger LOG = Logger.getLogger(SqsPublisher.class);

    @Inject
    SqsClient sqsClient;

    @ConfigProperty(name = "sqs.queue.classificacao.url")
    String classificacaoQueueUrl;

    @ConfigProperty(name = "hospital.id")
    String hospitalId;

    public void publicarParaClassificacao(String payload, String triagemId) {
        SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl(classificacaoQueueUrl)
                .messageBody(payload)
                .messageAttributes(Map.of(
                        "triagemId", stringAttr(triagemId),
                        "hospitalId", stringAttr(hospitalId),
                        "source", stringAttr("ms-triagem")
                ))
                .build();

        var result = sqsClient.sendMessage(request);
        LOG.debugf("Mensagem publicada no SQS: messageId=%s, triagemId=%s", result.messageId(), triagemId);
    }

    private MessageAttributeValue stringAttr(String value) {
        return MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(value)
                .build();
    }
}
