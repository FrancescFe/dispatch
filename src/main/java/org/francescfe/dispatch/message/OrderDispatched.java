package org.francescfe.dispatch.message;

import java.util.UUID;

public record OrderDispatched(
        UUID orderId,
        UUID processedById,
        String notes
) {
}
