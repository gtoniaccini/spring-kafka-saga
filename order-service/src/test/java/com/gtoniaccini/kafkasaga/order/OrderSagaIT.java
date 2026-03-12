package com.gtoniaccini.kafkasaga.order;

import com.gtoniaccini.kafkasaga.events.StockFailedEvent;
import com.gtoniaccini.kafkasaga.events.StockReservedEvent;
import com.gtoniaccini.kafkasaga.order.domain.Order;
import com.gtoniaccini.kafkasaga.order.domain.OrderRepository;
import com.gtoniaccini.kafkasaga.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class OrderSagaIT {

    @Container
    static final ConfluentKafkaContainer kafka =
            new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("order_db")
                    .withUsername("order_user")
                    .withPassword("order_pass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.stock-reserved}")
    String stockReservedTopic;

    @Value("${app.kafka.topics.stock-failed}")
    String stockFailedTopic;

    @Test
    void createOrder_initialStatus_isPending() throws Exception {
        Long orderId = createOrder();

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void sagaHappyPath_orderIsConfirmed() throws Exception {
        Long orderId = createOrder();

        kafkaTemplate.send(stockReservedTopic, String.valueOf(orderId),
                new StockReservedEvent(orderId, "PROD-001", 2));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Order order = orderRepository.findById(orderId).orElseThrow();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        });
    }

    @Test
    void sagaCompensation_orderIsCancelled() throws Exception {
        Long orderId = createOrder();

        kafkaTemplate.send(stockFailedTopic, String.valueOf(orderId),
                new StockFailedEvent(orderId, "PROD-001", "Insufficient stock"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Order order = orderRepository.findById(orderId).orElseThrow();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        });
    }

    private Long createOrder() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"PROD-001\",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }
}
