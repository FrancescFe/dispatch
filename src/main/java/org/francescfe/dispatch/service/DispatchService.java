package org.francescfe.dispatch.service;

import lombok.RequiredArgsConstructor;
import org.francescfe.dispatch.message.DispatchTracking;
import org.francescfe.dispatch.message.OrderCreated;
import org.francescfe.dispatch.message.OrderDispatched;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DispatchService {

    private static final String ORDER_DISPATCHED_TOPIC = "order.dispatched";
    private static final String DISPATCH_TRACKING_TOPIC = "dispatch.tracking";
    private static final String DISPATCHED_STATUS = "DISPATCHED";
    private final KafkaTemplate<String, Object> kafkaProducer;

    public void process(OrderCreated orderCreated) throws Exception {
        OrderDispatched orderDispatched = new OrderDispatched(orderCreated.orderId());
        DispatchTracking dispatchTracking = new DispatchTracking(orderCreated.orderId(), DISPATCHED_STATUS);

        kafkaProducer.send(ORDER_DISPATCHED_TOPIC, orderDispatched).get();
        kafkaProducer.send(DISPATCH_TRACKING_TOPIC, dispatchTracking).get();
    }
}
