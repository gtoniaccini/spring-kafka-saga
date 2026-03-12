package com.gtoniaccini.kafkasaga.inventory.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "processed_order_events")
public class ProcessedOrderEvent {

    @Id
    private Long orderId;

    @Column(nullable = false)
    private LocalDateTime processedAt;

    public ProcessedOrderEvent() {}

    public ProcessedOrderEvent(Long orderId) {
        this.orderId = orderId;
        this.processedAt = LocalDateTime.now();
    }

    public Long getOrderId() { return orderId; }
    public LocalDateTime getProcessedAt() { return processedAt; }
}
