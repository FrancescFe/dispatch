package org.francescfe.dispatch.message;

import java.util.UUID;

public record DispatchCompleted(UUID orderId, String date) {
}
