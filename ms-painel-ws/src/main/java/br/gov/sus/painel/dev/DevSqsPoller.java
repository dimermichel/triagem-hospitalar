package br.gov.sus.painel.dev;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

/**
 * Dev-mode SQS poller.
 * In production the sqs-fanout Lambda is triggered by the SQS event source.
 * Here we poll painel-queue every 2 seconds and broadcast each message to
 * all connected WebSocket clients via DevConnectionRegistry.
 */
@ApplicationScoped
@IfBuildProfile("dev")
public class DevSqsPoller {

    private static final Logger LOG = Logger.getLogger(DevSqsPoller.class);

    @Inject
    SqsClient sqsClient;

    @Inject
    DevConnectionRegistry registry;

    @ConfigProperty(name = "sqs.queue.painel.url")
    String queueUrl;

    @Scheduled(every = "2s")
    void poll() {
        var response = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(0)
                .build());

        for (var msg : response.messages()) {
            LOG.infof("Fan-out SQS dev: messageId=%s", msg.messageId());
            try {
                registry.broadcast(msg.body());
            } catch (Exception e) {
                LOG.errorf(e, "Erro no broadcast WS dev: messageId=%s", msg.messageId());
            } finally {
                sqsClient.deleteMessage(DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(msg.receiptHandle())
                        .build());
            }
        }
    }
}
