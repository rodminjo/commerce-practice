package com.rodminjo.commerce.inventory.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Message;
import com.rodminjo.commerce.common.outbox.relay.OutboxRelay;
import com.rodminjo.commerce.events.order.OrderCancelled;
import com.rodminjo.commerce.events.order.OrderLineItem;
import com.rodminjo.commerce.events.order.OrderPlaced;
import com.rodminjo.commerce.inventory.application.port.out.InventoryStockPort;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializerConfig;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.util.Properties;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Docker-free in→out integration test for the inventory service: produce an Order event to Kafka,
 * let the real {@code @KafkaListener} run the use case against a real embedded Postgres (Zonky),
 * and assert the atomic reservation side effects + the outbox event it appends.
 */
@SpringBootTest
@EmbeddedKafka(
    topics = {"order.placed", "order.cancelled", "inventory.reserved", "inventory.released"},
    partitions = 1,
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class InventoryReserveIntegrationTest {

  private static final String MOCK_REGISTRY = "mock://inventory-test";
  private static EmbeddedPostgres embeddedPg;

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) throws IOException {
    embeddedPg = EmbeddedPostgres.builder().start();
    String jdbcUrl = embeddedPg.getJdbcUrl("postgres", "postgres") + "&currentSchema=inventory_svc";
    registry.add("spring.datasource.url", () -> jdbcUrl);
    registry.add("spring.datasource.username", () -> "postgres");
    registry.add("spring.datasource.password", () -> "postgres");
    registry.add("spring.flyway.create-schemas", () -> "true");
    registry.add("spring.kafka.properties.schema.registry.url", () -> MOCK_REGISTRY);
    registry.add("outbox.relay.poll-interval-ms", () -> "3600000");
    registry.add(
        "spring.security.oauth2.resourceserver.jwt.issuer-uri",
        () -> "http://localhost:9999/realms/test");
  }

  @AfterAll
  static void stopEmbeddedPg() throws IOException {
    if (embeddedPg != null) {
      embeddedPg.close();
    }
  }

  @Autowired private InventoryStockPort stockPort;
  @Autowired private OutboxTestRepository outboxRepository;
  @Autowired private OutboxRelay outboxRelay;
  @Autowired private EmbeddedKafkaBroker embeddedKafka;

  private KafkaProducer<String, Message> producer() {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString());
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaProtobufSerializer.class);
    props.put(KafkaProtobufSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, MOCK_REGISTRY);
    return new KafkaProducer<>(props);
  }

  private void send(String topic, String key, Message event) {
    try (KafkaProducer<String, Message> producer = producer()) {
      producer.send(new ProducerRecord<>(topic, key, event)).get();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private int reserved(String productId) {
    return stockPort.find(productId).orElseThrow().reserved();
  }

  private static boolean awaitUntil(BooleanSupplier condition) {
    long deadline = System.currentTimeMillis() + 15_000;
    while (System.currentTimeMillis() < deadline) {
      if (condition.getAsBoolean()) {
        return true;
      }
      sleep(200);
    }
    return condition.getAsBoolean();
  }

  private static void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static OrderPlaced orderPlaced(String orderId, String productId, int qty) {
    return OrderPlaced.newBuilder()
        .setOrderId(orderId)
        .setCustomerId("customer-1")
        .addItems(
            OrderLineItem.newBuilder()
                .setProductId(productId)
                .setQuantity(qty)
                .setUnitPriceMinor(1000L)
                .build())
        .setTotalAmountMinor(1000L * qty)
        .setCurrency("KRW")
        .build();
  }

  @Test
  @DisplayName("AC-3: order.placed 수신 → 원자적 예약 → reserved 증가 + inventory.reserved 적재/발행")
  void reserveOnOrderPlaced() {
    String orderId = "order-reserve-1";
    int before = reserved("prod-1");

    send("order.placed", orderId, orderPlaced(orderId, "prod-1", 3));

    assertThat(awaitUntil(() -> reserved("prod-1") == before + 3)).isTrue();

    assertThat(outboxRepository.findByTopic("inventory.reserved"))
        .anySatisfy(e -> assertThat(e.getAggregateId()).isEqualTo(orderId));

    int published = outboxRelay.publishBatch();
    assertThat(published).isGreaterThanOrEqualTo(1);
  }

  @Test
  @DisplayName("AC-8: order.cancelled 수신 → 보상 복구 → reserved 원복 + inventory.released 적재")
  void releaseOnOrderCancelled() {
    String orderId = "order-reserve-release-1";
    int before = reserved("prod-1");

    send("order.placed", orderId, orderPlaced(orderId, "prod-1", 4));
    assertThat(awaitUntil(() -> reserved("prod-1") == before + 4)).isTrue();

    send(
        "order.cancelled",
        orderId,
        OrderCancelled.newBuilder().setOrderId(orderId).setReason("user-cancel").build());

    assertThat(awaitUntil(() -> reserved("prod-1") == before)).isTrue();

    assertThat(outboxRepository.findByTopic("inventory.released"))
        .anySatisfy(e -> assertThat(e.getAggregateId()).isEqualTo(orderId));
  }

  @Test
  @DisplayName("AC-4: 재고 초과 예약은 영향 행 0 → 미예약 (oversell 0)")
  void insufficientStockDoesNotReserve() {
    String orderId = "order-oversell-1";
    int before = reserved("prod-2"); // seeded stock 5

    send("order.placed", orderId, orderPlaced(orderId, "prod-2", 6));

    // Give the listener time to (fail to) process, then assert nothing changed.
    sleep(3_000);
    assertThat(reserved("prod-2")).isEqualTo(before);
    assertThat(outboxRepository.findByTopic("inventory.reserved"))
        .noneSatisfy(e -> assertThat(e.getAggregateId()).isEqualTo(orderId));
  }
}
