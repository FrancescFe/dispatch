package org.francescfe.dispatch.util;

import org.francescfe.dispatch.message.OrderCreated;

import java.util.UUID;

public class TestEventData {

    public static OrderCreated buildOrderCreated(UUID orderId, String item) {
        return new OrderCreated(orderId, item);
    }
}
