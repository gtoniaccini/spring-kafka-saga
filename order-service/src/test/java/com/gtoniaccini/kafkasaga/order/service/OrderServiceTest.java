package com.gtoniaccini.kafkasaga.order.service;

import com.gtoniaccini.kafkasaga.events.OrderCreatedEvent;
import com.gtoniaccini.kafkasaga.order.api.OrderRequest;
import com.gtoniaccini.kafkasaga.order.api.OrderResponse;
import com.gtoniaccini.kafkasaga.order.domain.Order;
import com.gtoniaccini.kafkasaga.order.domain.OrderRepository;
import com.gtoniaccini.kafkasaga.order.domain.OrderStatus;
import com.gtoniaccini.kafkasaga.order.exception.OrderNotFoundException;
import com.gtoniaccini.kafkasaga.order.kafka.OrderEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    OrderRepository repository;

    @Mock
    OrderEventPublisher publisher;

    @InjectMocks
    OrderService service;

    @Test
    void create_saves_order_and_publishes_event() {
        Order saved = order(1L, "PROD-001", 3, OrderStatus.PENDING);
        when(repository.save(any())).thenReturn(saved);

        OrderResponse response = service.create(new OrderRequest("PROD-001", 3));

        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.productId()).isEqualTo("PROD-001");

        ArgumentCaptor<OrderCreatedEvent> captor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(publisher).publishOrderCreated(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo(1L);
        assertThat(captor.getValue().productId()).isEqualTo("PROD-001");
        assertThat(captor.getValue().quantity()).isEqualTo(3);
    }

    @Test
    void confirm_sets_status_to_confirmed() {
        Order pending = order(1L, "PROD-001", 3, OrderStatus.PENDING);
        when(repository.findById(1L)).thenReturn(Optional.of(pending));

        service.confirm(1L);

        assertThat(pending.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(repository).save(pending);
    }

    @Test
    void cancel_sets_status_to_cancelled() {
        Order pending = order(1L, "PROD-001", 3, OrderStatus.PENDING);
        when(repository.findById(1L)).thenReturn(Optional.of(pending));

        service.cancel(1L);

        assertThat(pending.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(repository).save(pending);
    }

    @Test
    void findById_throws_when_not_found() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("99");
    }

    private Order order(Long id, String productId, int qty, OrderStatus status) {
        Order o = new Order();
        o.setId(id);
        o.setProductId(productId);
        o.setQuantity(qty);
        o.setStatus(status);
        o.setCreatedAt(LocalDateTime.now());
        return o;
    }
}
