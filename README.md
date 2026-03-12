# Spring Kafka Saga

![CI](https://github.com/gtoniaccini/spring-kafka-saga/actions/workflows/ci.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-21-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-brightgreen)
![Kafka](https://img.shields.io/badge/Apache%20Kafka-3.7-black)
![Liquibase](https://img.shields.io/badge/Liquibase-4.29-lightgrey)

Reference implementation of the **Saga choreography** pattern with Apache Kafka and Spring Boot 3.
Two microservices communicate exclusively through events — no direct HTTP calls between them.
Demonstrates idempotent Kafka consumers, dead-letter topics, Liquibase schema management, and full integration testing with Testcontainers.

The `order-service` and `inventory-service` are working demos — swap their business logic with your own domain and keep the Kafka infrastructure as-is.

## What this project shows

- **Saga choreography**: each service reacts to events and publishes its own — no central orchestrator
- **Idempotent consumers**: the `processed_order_events` table prevents duplicate Kafka deliveries from modifying stock twice
- **Dead Letter Topic**: after 3 exponential-backoff retries, failed messages are forwarded to `<topic>.DLT` instead of being silently dropped
- **Liquibase**: schema migrations replace `ddl-auto: update` — every structural change is versioned and repeatable
- **Environment-variable–driven config**: credentials and connection strings are never hardcoded; env vars override safe local defaults
- **Actuator probes**: `/actuator/health/liveness` and `/actuator/health/readiness` ready for Docker/Kubernetes health checks

## Architecture

```
POST /api/orders
       │
       ▼
 order-service ──── order.created ────► inventory-service
       │                                       │
       │◄──── stock.reserved ─────────────────┤  (stock OK → decrement + reserve)
       │                                       │
       └◄──── stock.failed ───────────────────┘  (stock KO → publish failure reason)
       │
  CONFIRMED / CANCELLED
```

### Saga flow

1. Client sends `POST /api/orders` → order-service saves order with status **PENDING**
2. order-service publishes `OrderCreatedEvent` to topic `order.created`
3. inventory-service consumes the event, checks idempotency, then checks stock:
   - **Stock available** → decrements quantity, publishes `StockReservedEvent` to `stock.reserved`
   - **Insufficient stock or product not found** → publishes `StockFailedEvent` to `stock.failed`
4. order-service consumes the reply:
   - `StockReservedEvent` → status **CONFIRMED**
   - `StockFailedEvent` → status **CANCELLED** (compensation transaction)

## Tech stack

- Java 21
- Spring Boot 3.4
- Spring Kafka — JSON serialization with `__TypeId__` type headers
- Apache Kafka 3.7 — KRaft mode (no Zookeeper)
- Spring Data JPA
- PostgreSQL 16 (one database per service)
- Liquibase 4.29
- Spring Boot Actuator
- Testcontainers — ConfluentKafkaContainer + PostgreSQLContainer
- Awaitility — async assertions in integration tests

## Modules

| Module | Responsibility | Port |
|---|---|---|
| `events` | Shared event records (`OrderCreatedEvent`, `StockReservedEvent`, `StockFailedEvent`) — no Spring dependency | — |
| `order-service` | REST API, Saga state machine (PENDING → CONFIRMED / CANCELLED), Kafka producer + consumer | 8081 |
| `inventory-service` | Stock management, idempotent Kafka consumer, stock reservation logic | 8082 |

## Events

| Record | Topic | Direction |
|---|---|---|
| `OrderCreatedEvent(orderId, productId, quantity)` | `order.created` | order-service → inventory-service |
| `StockReservedEvent(orderId, productId, quantity)` | `stock.reserved` | inventory-service → order-service |
| `StockFailedEvent(orderId, productId, reason)` | `stock.failed` | inventory-service → order-service |

## Package structure

### order-service — `com.gtoniaccini.kafkasaga.order`

| Package | Responsibility |
|---|---|
| `api` | `OrderController` (POST/GET), `OrderRequest`, `OrderResponse` |
| `domain` | `Order` entity, `OrderStatus` enum, `OrderRepository` |
| `service` | `OrderService` — create, confirm, cancel |
| `kafka` | `OrderEventPublisher`, `InventoryReplyListener` |
| `config` | `KafkaConfig` — `DefaultErrorHandler` with backoff + DLT |
| `exception` | `OrderNotFoundException`, `RestExceptionHandler` |

### inventory-service — `com.gtoniaccini.kafkasaga.inventory`

| Package | Responsibility |
|---|---|
| `domain` | `InventoryItem` entity, `InventoryRepository`, `ProcessedOrderEvent`, `ProcessedOrderEventRepository` |
| `kafka` | `OrderEventListener` (idempotent), `StockEventPublisher` |
| `config` | `KafkaConfig` — `DefaultErrorHandler` with backoff + DLT |

## Reuse in your project

### 1. Copy the infrastructure

The following classes contain no business logic and can be copied as-is:

- `config/KafkaConfig.java` in both services (error handler + DLT)
- `domain/ProcessedOrderEvent.java` + `ProcessedOrderEventRepository.java` (idempotency pattern)
- `db/changelog/db.changelog-master.yaml` changeset id=3 (processed_order_events table)

### 2. Replace the domain

Remove the `order` and `inventory` packages. Replace them with your own services. The only coupling between them is through the `events` module records — update those to match your use case.

### 3. Adapt the events module

The `events` module must remain a plain JAR with no Spring dependencies. Add your own records following the same pattern:

```java
public record YourDomainEvent(Long aggregateId, String field, ...) {}
```

### 4. Wire up idempotency in your listener

```java
@KafkaListener(topics = "${app.kafka.topics.your-topic}", groupId = "${spring.kafka.consumer.group-id}")
@Transactional
public void onYourEvent(YourDomainEvent event) {
    if (processedEventRepository.existsById(event.aggregateId())) {
        log.warn("Duplicate event for id={}, skipping", event.aggregateId());
        return;
    }
    // your business logic here
    processedEventRepository.save(new ProcessedOrderEvent(event.aggregateId()));
}
```

### 5. Configure topics and credentials via environment variables

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
  datasource:
    url: ${YOUR_DB_URL:jdbc:postgresql://localhost:5432/your_db}
    username: ${YOUR_DB_USERNAME:your_user}
    password: ${YOUR_DB_PASSWORD:your_pass}
```

## Configuration reference

```yaml
# order-service
app:
  kafka:
    topics:
      order-created: order.created   # topic this service produces to
      stock-reserved: stock.reserved # reply topic (success)
      stock-failed: stock.failed     # reply topic (compensation)

# inventory-service
# same app.kafka.topics block — both services share the same topic names
```

Dead Letter Topics are auto-named `<original-topic>.DLT` by `DeadLetterPublishingRecoverer`.

## CI/CD

GitHub Actions runs on every push and pull request to `main`:

| Step | Command | Notes |
|---|---|---|
| Unit tests | `mvn verify -DskipITs` | No Docker required |
| Integration tests | `mvn verify -Dsurefire.skip=true` | Testcontainers — Docker is available on `ubuntu-latest` |

Workflow file: [`.github/workflows/ci.yml`](.github/workflows/ci.yml)

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker (for local infrastructure and integration tests)

## Run locally

**1. Start infrastructure**

```bash
docker compose up -d
```

Starts:
- Kafka on `localhost:9092` (KRaft mode, no Zookeeper)
- `order-db` PostgreSQL on `localhost:5432`
- `inventory-db` PostgreSQL on `localhost:5433`

**2. Build**

```bash
mvn clean package -DskipTests
```

**3. Start services**

```bash
# Terminal 1
java -jar order-service/target/order-service-0.0.1-SNAPSHOT.jar

# Terminal 2
java -jar inventory-service/target/inventory-service-0.0.1-SNAPSHOT.jar
```

## Example requests

**Happy path** — `PROD-001` has 100 units in stock:

```bash
curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productId":"PROD-001","quantity":5}' | jq
```

Check the order status (replace `1` with the actual id):

```bash
curl -s http://localhost:8081/api/orders/1 | jq
# expected: "status": "CONFIRMED"
```

**Compensation** — `PROD-003` has 0 units:

```bash
curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productId":"PROD-003","quantity":1}' | jq

curl -s http://localhost:8081/api/orders/2 | jq
# expected: "status": "CANCELLED"
```

## Seeded inventory (via Liquibase changeset 2)

| Product | Initial stock |
|---|---|
| PROD-001 | 100 units |
| PROD-002 | 5 units |
| PROD-003 | 0 units |

## Architecture notes

- **At-least-once delivery**: Kafka guarantees at-least-once delivery by design. Without idempotency, a redelivered `OrderCreatedEvent` would decrement stock twice. The `processed_order_events` table (checked and written in the same `@Transactional` boundary) prevents this.
- **No `ddl-auto: update`**: Liquibase owns the schema. `ddl-auto: validate` ensures Hibernate's view of the schema matches what Liquibase created, and fails fast at startup if they diverge.
- **KRaft Kafka**: uses `apache/kafka:3.7.0` in KRaft mode — no Zookeeper container needed, shorter `docker compose up` and simpler local setup.
- **Polymorphic Kafka producer**: both services use `KafkaTemplate<String, Object>` and set `spring.json.add.type.headers: true`. Consumers trust `com.gtoniaccini.kafkasaga.events` for type resolution via the `__TypeId__` header.
- **Single group-id per service**: each service has its own consumer group, so both receive every event on shared topics independently.

## Testing

### Unit tests (`mvn test`)

No Spring context, no Docker required.

| Test class | What it covers |
|---|---|
| `OrderServiceTest` | Order creation publishes event, confirm/cancel status transitions, not-found exception |
| `OrderControllerTest` | 201 on valid create, 400 on invalid body, 200 on get, 404 on missing order |
| `OrderEventListenerTest` | Sufficient stock → reserves and decrements, insufficient → StockFailedEvent, unknown product → StockFailedEvent |

### Integration tests (`mvn verify`, requires Docker)

Each IT test spins up a `ConfluentKafkaContainer` and a `PostgreSQLContainer` via Testcontainers.

| Test class | What it verifies |
|---|---|
| `OrderSagaIT` | Full saga: initial status is PENDING, happy path ends in CONFIRMED, compensation ends in CANCELLED |
| `InventoryListenerIT` | Stock is decremented on sufficient stock, left unchanged on insufficient stock, left unchanged for unknown product |

Integration tests are annotated with `@Testcontainers(disabledWithoutDocker = true)` — they are skipped automatically if Docker is unavailable.
