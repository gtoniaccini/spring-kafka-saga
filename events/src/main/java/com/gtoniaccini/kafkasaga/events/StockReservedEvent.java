package com.gtoniaccini.kafkasaga.events;

public record StockReservedEvent(Long orderId, String productId, int quantity) {
}
