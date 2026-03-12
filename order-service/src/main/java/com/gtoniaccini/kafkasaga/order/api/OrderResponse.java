package com.gtoniaccini.kafkasaga.order.api;

import com.gtoniaccini.kafkasaga.order.domain.OrderStatus;

import java.time.LocalDateTime;

public record OrderResponse(
        Long id,
        String productId,
        int quantity,
        OrderStatus status,
        LocalDateTime createdAt
) {
}
