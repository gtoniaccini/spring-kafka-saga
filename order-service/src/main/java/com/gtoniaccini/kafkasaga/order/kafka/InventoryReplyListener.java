package com.gtoniaccini.kafkasaga.order.kafka;

import com.gtoniaccini.kafkasaga.events.StockFailedEvent;
import com.gtoniaccini.kafkasaga.events.StockReservedEvent;
import com.gtoniaccini.kafkasaga.order.service.OrderService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryReplyListener {

    private final OrderService orderService;

    public InventoryReplyListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @KafkaListener(topics = "${app.kafka.topics.stock-reserved}", groupId = "${spring.kafka.consumer.group-id}")
    public void onStockReserved(StockReservedEvent event) {
        orderService.confirm(event.orderId());
    }

    @KafkaListener(topics = "${app.kafka.topics.stock-failed}", groupId = "${spring.kafka.consumer.group-id}")
    public void onStockFailed(StockFailedEvent event) {
        orderService.cancel(event.orderId());
    }
}
