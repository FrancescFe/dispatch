package org.francescfe.dispatch.message;

import java.util.UUID;

public record OrderCreated(UUID orderId, String item) {
}
