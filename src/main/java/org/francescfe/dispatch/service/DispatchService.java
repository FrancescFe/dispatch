package org.francescfe.dispatch.service;

import org.francescfe.dispatch.message.DispatchPreparing;
import org.francescfe.dispatch.message.OrderCreated;
import org.francescfe.dispatch.message.OrderDispatched;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class DispatchService {

    private static final String ORDER_DISPATCHED_TOPIC = "order.dispatched";
    private static final String DISPATCH_TRACKING_TOPIC = "dispatch.tracking";
    private final KafkaTemplate<String, Object> kafkaProducer;

    public DispatchService(KafkaTemplate<String, Object> kafkaProducer) {
        this.kafkaProducer = kafkaProducer;
    }

    public void process(OrderCreated orderCreated) throws Exception {
        OrderDispatched orderDispatched = new OrderDispatched(orderCreated.orderId());
        kafkaProducer.send(ORDER_DISPATCHED_TOPIC, orderDispatched).get();

        DispatchPreparing dispatchPreparing = new DispatchPreparing(orderCreated.orderId());
        kafkaProducer.send(DISPATCH_TRACKING_TOPIC, dispatchPreparing).get();
    }
}
