package org.francescfe.dispatch.handler;

import org.francescfe.dispatch.message.OrderCreated;
import org.francescfe.dispatch.service.DispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedHandler.class);

    private final DispatchService dispatchService;

    public OrderCreatedHandler(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @KafkaListener(
            id = "orderConsumerClient",
            topics = "order.created",
            groupId = "dispatch.order.created.consumer",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void listen(OrderCreated payload) {
        log.info("Received order created payload: {}", payload);
        try {
            dispatchService.process(payload);
        } catch (Exception e) {
            log.error("Processing failure", e);
        }
    }
}
