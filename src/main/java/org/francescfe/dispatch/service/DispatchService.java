package org.francescfe.dispatch.service;

import org.francescfe.dispatch.message.DispatchPreparing;
import org.francescfe.dispatch.message.OrderCreated;
import org.francescfe.dispatch.message.OrderDispatched;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static java.util.UUID.randomUUID;

@Service
public class DispatchService {

    private static final Logger log = LoggerFactory.getLogger(DispatchService.class);
    private static final String ORDER_DISPATCHED_TOPIC = "order.dispatched";
    private static final String DISPATCH_TRACKING_TOPIC = "dispatch.tracking";
    private static final UUID APPLICATION_ID = randomUUID();
    private final KafkaTemplate<String, Object> kafkaProducer;

    public DispatchService(KafkaTemplate<String, Object> kafkaProducer) {
        this.kafkaProducer = kafkaProducer;
    }

    public void process(OrderCreated orderCreated) throws Exception {
        DispatchPreparing dispatchPreparing = new DispatchPreparing(orderCreated.orderId());
        kafkaProducer.send(DISPATCH_TRACKING_TOPIC, dispatchPreparing).get();

        OrderDispatched orderDispatched = new OrderDispatched(
                orderCreated.orderId(),
                APPLICATION_ID,
                "Dispatched: " + orderCreated.item()
        );
        kafkaProducer.send(ORDER_DISPATCHED_TOPIC, orderDispatched).get();

        log.info("Sent messages: orderId: {} - processedById: {}", orderCreated.orderId(), APPLICATION_ID);
    }
}
