package com.gtoniaccini.kafkasaga.inventory.kafka;

import com.gtoniaccini.kafkasaga.events.OrderCreatedEvent;
import com.gtoniaccini.kafkasaga.events.StockFailedEvent;
import com.gtoniaccini.kafkasaga.events.StockReservedEvent;
import com.gtoniaccini.kafkasaga.inventory.domain.InventoryItem;
import com.gtoniaccini.kafkasaga.inventory.domain.InventoryRepository;
import com.gtoniaccini.kafkasaga.inventory.domain.ProcessedOrderEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEventListenerTest {

    @Mock
    InventoryRepository repository;

    @Mock
    StockEventPublisher publisher;

    @Mock
    ProcessedOrderEventRepository processedEventRepository;

    @InjectMocks
    OrderEventListener listener;

    @Test
    void sufficientStock_reservesAndPublishesReservedEvent() {
        InventoryItem item = item("PROD-001", 100);
        when(repository.findByProductId("PROD-001")).thenReturn(Optional.of(item));
        when(repository.save(any())).thenReturn(item);

        listener.onOrderCreated(new OrderCreatedEvent(1L, "PROD-001", 10));

        assertThat(item.getAvailableQuantity()).isEqualTo(90);

        ArgumentCaptor<StockReservedEvent> captor = ArgumentCaptor.forClass(StockReservedEvent.class);
        verify(publisher).publishStockReserved(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo(1L);
        assertThat(captor.getValue().quantity()).isEqualTo(10);
        verifyNoMoreInteractions(publisher);
    }

    @Test
    void insufficientStock_publishesFailedEvent() {
        InventoryItem item = item("PROD-002", 5);
        when(repository.findByProductId("PROD-002")).thenReturn(Optional.of(item));

        listener.onOrderCreated(new OrderCreatedEvent(2L, "PROD-002", 10));

        verify(publisher, never()).publishStockReserved(any());
        ArgumentCaptor<StockFailedEvent> captor = ArgumentCaptor.forClass(StockFailedEvent.class);
        verify(publisher).publishStockFailed(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo(2L);
        assertThat(captor.getValue().reason()).contains("Insufficient");
    }

    @Test
    void unknownProduct_publishesFailedEvent() {
        when(repository.findByProductId("UNKNOWN")).thenReturn(Optional.empty());

        listener.onOrderCreated(new OrderCreatedEvent(3L, "UNKNOWN", 1));

        verify(publisher, never()).publishStockReserved(any());
        ArgumentCaptor<StockFailedEvent> captor = ArgumentCaptor.forClass(StockFailedEvent.class);
        verify(publisher).publishStockFailed(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo(3L);
        assertThat(captor.getValue().reason()).contains("not found");
    }

    private InventoryItem item(String productId, int qty) {
        InventoryItem item = new InventoryItem();
        item.setProductId(productId);
        item.setAvailableQuantity(qty);
        return item;
    }
}
