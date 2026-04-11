package br.gov.sus.manchester.dev;

import br.gov.sus.manchester.service.ClassificacaoLambdaHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dev-mode SQS poller for the classificacao-queue.
 * In production this Lambda is triggered by the SQS event source automatically.
 * In dev mode this scheduler polls every 2 seconds and delegates to the
 * existing ClassificacaoLambdaHandler, keeping all classification logic in one place.
 */
@ApplicationScoped
@IfBuildProfile("dev")
public class DevClassificacaoPoller {

    private static final Logger LOG = Logger.getLogger(DevClassificacaoPoller.class);

    @Inject
    SqsClient sqsClient;

    @Inject
    ClassificacaoLambdaHandler handler;

    @ConfigProperty(name = "sqs.queue.classificacao.url")
    String queueUrl;

    @Scheduled(every = "2s")
    void poll() {
        var response = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(0)
                .messageAttributeNames("All")
                .build());

        if (response.messages().isEmpty()) return;

        var sqsEvent = new SQSEvent();
        sqsEvent.setRecords(response.messages().stream()
                .map(this::toSqsMessage)
                .toList());

        LOG.infof("Processando %d mensagem(ns) da classificacao-queue", response.messages().size());
        handler.handleRequest(sqsEvent, null);

        response.messages().forEach(msg ->
                sqsClient.deleteMessage(DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(msg.receiptHandle())
                        .build())
        );
    }

    private SQSEvent.SQSMessage toSqsMessage(Message msg) {
        var sqsMsg = new SQSEvent.SQSMessage();
        sqsMsg.setMessageId(msg.messageId());
        sqsMsg.setReceiptHandle(msg.receiptHandle());
        sqsMsg.setBody(msg.body());

        if (!msg.messageAttributes().isEmpty()) {
            Map<String, SQSEvent.MessageAttribute> attrs = new HashMap<>();
            msg.messageAttributes().forEach((k, v) -> {
                var attr = new SQSEvent.MessageAttribute();
                attr.setStringValue(v.stringValue());
                attr.setDataType(v.dataType());
                attrs.put(k, attr);
            });
            sqsMsg.setMessageAttributes(attrs);
        }
        return sqsMsg;
    }
}
