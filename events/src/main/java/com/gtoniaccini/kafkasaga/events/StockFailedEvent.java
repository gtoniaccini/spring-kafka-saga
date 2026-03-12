package com.gtoniaccini.kafkasaga.events;

public record StockFailedEvent(Long orderId, String productId, String reason) {
}
