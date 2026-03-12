package com.gtoniaccini.kafkasaga.inventory;

import com.gtoniaccini.kafkasaga.events.OrderCreatedEvent;
import com.gtoniaccini.kafkasaga.inventory.domain.InventoryItem;
import com.gtoniaccini.kafkasaga.inventory.domain.InventoryRepository;
import com.gtoniaccini.kafkasaga.inventory.domain.ProcessedOrderEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class InventoryListenerIT {

    @Container
    static final ConfluentKafkaContainer kafka =
            new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("inventory_db")
                    .withUsername("inventory_user")
                    .withPassword("inventory_pass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    InventoryRepository inventoryRepository;

    @Autowired
    ProcessedOrderEventRepository processedOrderEventRepository;

    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.order-created}")
    String orderCreatedTopic;

    @BeforeEach
    void setUp() {
        processedOrderEventRepository.deleteAll();
        inventoryRepository.deleteAll();
        inventoryRepository.save(item("PROD-001", 100));
        inventoryRepository.save(item("PROD-002", 5));
        inventoryRepository.save(item("PROD-003", 0));
    }

    @Test
    void sufficientStock_decrementsQuantity() {
        kafkaTemplate.send(orderCreatedTopic, "1", new OrderCreatedEvent(1L, "PROD-001", 10));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            InventoryItem updated = inventoryRepository.findByProductId("PROD-001").orElseThrow();
            assertThat(updated.getAvailableQuantity()).isEqualTo(90);
        });
    }

    @Test
    void insufficientStock_doesNotDecrementQuantity() {
        kafkaTemplate.send(orderCreatedTopic, "2", new OrderCreatedEvent(2L, "PROD-002", 10));

        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            InventoryItem item = inventoryRepository.findByProductId("PROD-002").orElseThrow();
            assertThat(item.getAvailableQuantity()).isEqualTo(5);
        });
    }

    @Test
    void unknownProduct_doesNotModifyInventory() {
        long countBefore = inventoryRepository.count();
        kafkaTemplate.send(orderCreatedTopic, "3", new OrderCreatedEvent(3L, "UNKNOWN-PROD", 1));

        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(8)).untilAsserted(() ->
            assertThat(inventoryRepository.count()).isEqualTo(countBefore)
        );
    }

    private InventoryItem item(String productId, int qty) {
        InventoryItem item = new InventoryItem();
        item.setProductId(productId);
        item.setAvailableQuantity(qty);
        return item;
    }
}
