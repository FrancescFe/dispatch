package org.francescfe.dispatch.handler;

import org.francescfe.dispatch.exception.NonRetryableException;
import org.francescfe.dispatch.exception.RetryableException;
import org.francescfe.dispatch.message.OrderCreated;
import org.francescfe.dispatch.service.DispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@KafkaListener(
        id = "orderConsumerClient",
        topics = "order.created",
        groupId = "dispatch.order.created.consumer",
        containerFactory = "kafkaListenerContainerFactory"
)
public class OrderCreatedHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedHandler.class);

    private final DispatchService dispatchService;

    public OrderCreatedHandler(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @KafkaHandler
    public void listen(
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Payload OrderCreated payload
    ) {
        log.info("Received message: partition: {} - key: {} - payload: {}", partition, key, payload);
        try {
            dispatchService.process(key, payload);
        } catch (RetryableException e) {
            log.warn("Retryable exception: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("NonRetryable exception: {}", e.getMessage());
            throw new NonRetryableException(e);
        }
    }
}
