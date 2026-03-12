package com.gtoniaccini.kafkasaga.events;

public record OrderCreatedEvent(Long orderId, String productId, int quantity) {
}
