package com.gtoniaccini.kafkasaga.order.service;

import com.gtoniaccini.kafkasaga.events.OrderCreatedEvent;
import com.gtoniaccini.kafkasaga.order.api.OrderRequest;
import com.gtoniaccini.kafkasaga.order.api.OrderResponse;
import com.gtoniaccini.kafkasaga.order.domain.Order;
import com.gtoniaccini.kafkasaga.order.domain.OrderRepository;
import com.gtoniaccini.kafkasaga.order.domain.OrderStatus;
import com.gtoniaccini.kafkasaga.order.exception.OrderNotFoundException;
import com.gtoniaccini.kafkasaga.order.kafka.OrderEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
public class OrderService {

    private final OrderRepository repository;
    private final OrderEventPublisher publisher;

    public OrderService(OrderRepository repository, OrderEventPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    public OrderResponse create(OrderRequest request) {
        Order order = new Order();
        order.setProductId(request.productId());
        order.setQuantity(request.quantity());
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(LocalDateTime.now());
        order = repository.save(order);

        publisher.publishOrderCreated(
                new OrderCreatedEvent(order.getId(), order.getProductId(), order.getQuantity()));

        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(Long id) {
        return repository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    public void confirm(Long orderId) {
        repository.findById(orderId).ifPresent(order -> {
            order.setStatus(OrderStatus.CONFIRMED);
            repository.save(order);
        });
    }

    public void cancel(Long orderId) {
        repository.findById(orderId).ifPresent(order -> {
            order.setStatus(OrderStatus.CANCELLED);
            repository.save(order);
        });
    }

    private OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getProductId(),
                order.getQuantity(),
                order.getStatus(),
                order.getCreatedAt()
        );
    }
}
