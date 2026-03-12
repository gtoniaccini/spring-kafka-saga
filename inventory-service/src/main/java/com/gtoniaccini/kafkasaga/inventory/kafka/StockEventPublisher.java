package com.gtoniaccini.kafkasaga.inventory.kafka;

import com.gtoniaccini.kafkasaga.events.StockFailedEvent;
import com.gtoniaccini.kafkasaga.events.StockReservedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class StockEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String stockReservedTopic;
    private final String stockFailedTopic;

    public StockEventPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                               @Value("${app.kafka.topics.stock-reserved}") String stockReservedTopic,
                               @Value("${app.kafka.topics.stock-failed}") String stockFailedTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.stockReservedTopic = stockReservedTopic;
        this.stockFailedTopic = stockFailedTopic;
    }

    public void publishStockReserved(StockReservedEvent event) {
        kafkaTemplate.send(stockReservedTopic, String.valueOf(event.orderId()), event);
    }

    public void publishStockFailed(StockFailedEvent event) {
        kafkaTemplate.send(stockFailedTopic, String.valueOf(event.orderId()), event);
    }
}
