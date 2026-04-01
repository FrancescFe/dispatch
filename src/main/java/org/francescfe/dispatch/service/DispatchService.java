package org.francescfe.dispatch.service;

import org.francescfe.dispatch.client.StockServiceClient;
import org.francescfe.dispatch.message.DispatchCompleted;
import org.francescfe.dispatch.message.DispatchPreparing;
import org.francescfe.dispatch.message.OrderCreated;
import org.francescfe.dispatch.message.OrderDispatched;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

import static java.util.UUID.randomUUID;

@Service
public class DispatchService {

    private static final Logger log = LoggerFactory.getLogger(DispatchService.class);
    private static final String ORDER_DISPATCHED_TOPIC = "order.dispatched";
    private static final String DISPATCH_TRACKING_TOPIC = "dispatch.tracking";
    private static final UUID APPLICATION_ID = randomUUID();
    private final KafkaTemplate<String, Object> kafkaProducer;
    private final StockServiceClient stockServiceClient;

    public DispatchService(KafkaTemplate<String, Object> kafkaProducer, StockServiceClient stockServiceClient) {
        this.kafkaProducer = kafkaProducer;
        this.stockServiceClient = stockServiceClient;
    }

    public void process(String key, OrderCreated orderCreated) throws Exception {
        String available = stockServiceClient.checkAvailability(orderCreated.item());

        if (Boolean.parseBoolean(available)) {
            DispatchPreparing dispatchPreparing = new DispatchPreparing(orderCreated.orderId());
            kafkaProducer.send(DISPATCH_TRACKING_TOPIC, key, dispatchPreparing).get();

            OrderDispatched orderDispatched = new OrderDispatched(
                    orderCreated.orderId(),
                    APPLICATION_ID,
                    "Dispatched: " + orderCreated.item()
            );
            kafkaProducer.send(ORDER_DISPATCHED_TOPIC, key, orderDispatched).get();

            DispatchCompleted dispatchCompleted = new DispatchCompleted(
                    orderCreated.orderId(),
                    Instant.now().toString()
            );
            kafkaProducer.send(DISPATCH_TRACKING_TOPIC, key, dispatchCompleted).get();

            log.info("Sent messages: key: {} - orderId: {} - processedById: {}", key, orderCreated.orderId(), APPLICATION_ID);
        } else {
            log.info("Item {} is not available", orderCreated.item());
        }
    }
}
