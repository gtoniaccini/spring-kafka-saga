package com.gtoniaccini.kafkasaga.inventory.kafka;

import com.gtoniaccini.kafkasaga.events.OrderCreatedEvent;
import com.gtoniaccini.kafkasaga.events.StockFailedEvent;
import com.gtoniaccini.kafkasaga.events.StockReservedEvent;
import com.gtoniaccini.kafkasaga.inventory.domain.InventoryRepository;
import com.gtoniaccini.kafkasaga.inventory.domain.ProcessedOrderEvent;
import com.gtoniaccini.kafkasaga.inventory.domain.ProcessedOrderEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final InventoryRepository repository;
    private final StockEventPublisher publisher;
    private final ProcessedOrderEventRepository processedEventRepository;

    public OrderEventListener(InventoryRepository repository,
                              StockEventPublisher publisher,
                              ProcessedOrderEventRepository processedEventRepository) {
        this.repository = repository;
        this.publisher = publisher;
        this.processedEventRepository = processedEventRepository;
    }

    @KafkaListener(topics = "${app.kafka.topics.order-created}", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onOrderCreated(OrderCreatedEvent event) {
        if (processedEventRepository.existsById(event.orderId())) {
            log.warn("Duplicate OrderCreatedEvent for orderId={}, skipping", event.orderId());
            return;
        }

        log.info("Processing order: orderId={}, productId={}, qty={}",
                event.orderId(), event.productId(), event.quantity());

        repository.findByProductId(event.productId()).ifPresentOrElse(
                item -> {
                    if (item.getAvailableQuantity() >= event.quantity()) {
                        item.setAvailableQuantity(item.getAvailableQuantity() - event.quantity());
                        repository.save(item);
                        publisher.publishStockReserved(
                                new StockReservedEvent(event.orderId(), event.productId(), event.quantity()));
                    } else {
                        publisher.publishStockFailed(new StockFailedEvent(
                                event.orderId(), event.productId(),
                                "Insufficient stock: available=" + item.getAvailableQuantity()));
                    }
                },
                () -> publisher.publishStockFailed(new StockFailedEvent(
                        event.orderId(), event.productId(), "Product not found: " + event.productId()))
        );

        processedEventRepository.save(new ProcessedOrderEvent(event.orderId()));
    }
}
