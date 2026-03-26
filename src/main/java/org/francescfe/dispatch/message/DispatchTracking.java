package org.francescfe.dispatch.message;

import java.util.UUID;

public record DispatchTracking(UUID orderId, String status) {
}
