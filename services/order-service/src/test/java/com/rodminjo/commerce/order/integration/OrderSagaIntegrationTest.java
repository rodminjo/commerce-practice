package com.rodminjo.commerce.order.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Message;
import com.rodminjo.commerce.events.inventory.InventoryReserved;
import com.rodminjo.commerce.events.inventory.ReservedItem;
import com.rodminjo.commerce.events.payment.PaymentCompleted;
import com.rodminjo.commerce.events.payment.PaymentFailed;
import com.rodminjo.commerce.order.adapter.out.persistence.jpa.OrderJpaRepository;
import com.rodminjo.commerce.order.application.port.in.CancelOrderUseCase;
import com.rodminjo.commerce.order.application.port.in.CancelOrderUseCase.CancelOrderCommand;
import com.rodminjo.commerce.order.application.port.in.PlaceOrderUseCase;
import com.rodminjo.commerce.order.application.port.in.PlaceOrderUseCase.PlaceOrderCommand;
import com.rodminjo.commerce.order.application.port.in.PlaceOrderUseCase.PlaceOrderCommand.OrderItemCommand;
import com.rodminjo.commerce.order.domain.model.OrderStatus;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializerConfig;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
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
 * Docker-free in→out integration test for the Order Saga orchestrator. Drives the order through the
 * inbound saga events (inventory.reserved / payment.completed / payment.failed) and the cancel use
 * case against real embedded Postgres (Zonky) + in-JVM Kafka, asserting the resulting order state
 * transitions and the compensation/forward events appended to the outbox.
 */
@SpringBootTest
@EmbeddedKafka(
    topics = {
      "order.placed",
      "order.cancelled",
      "payment.requested",
      "inventory.reserved",
      "payment.completed",
      "payment.failed"
    },
    partitions = 1,
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class OrderSagaIntegrationTest {

  private static final String MOCK_REGISTRY = "mock://order-saga-test";
  private static EmbeddedPostgres embeddedPg;

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) throws IOException {
    embeddedPg = EmbeddedPostgres.builder().start();
    String jdbcUrl = embeddedPg.getJdbcUrl("postgres", "postgres") + "&currentSchema=order_svc";
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

  @Autowired private PlaceOrderUseCase placeOrderUseCase;
  @Autowired private CancelOrderUseCase cancelOrderUseCase;
  @Autowired private OrderJpaRepository orderJpaRepository;
  @Autowired private OutboxTestRepository outboxRepository;
  @Autowired private EmbeddedKafkaBroker embeddedKafka;

  private UUID placeOrder() {
    PlaceOrderCommand cmd =
        new PlaceOrderCommand(
            "customer-saga", List.of(new OrderItemCommand("prod-1", 2, 1000L)), "KRW");
    return placeOrderUseCase.place(cmd).orderId();
  }

  private void send(String topic, String key, Message event) {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString());
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaProtobufSerializer.class);
    props.put(KafkaProtobufSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, MOCK_REGISTRY);
    try (KafkaProducer<String, Message> producer = new KafkaProducer<>(props)) {
      producer.send(new ProducerRecord<>(topic, key, event)).get();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private OrderStatus statusOf(UUID orderId) {
    return orderJpaRepository.findById(orderId).orElseThrow().getStatus();
  }

  private boolean hasOutbox(UUID orderId, String topic) {
    return outboxRepository.findByAggregateId(orderId.toString()).stream()
        .anyMatch(e -> e.getTopic().equals(topic));
  }

  private boolean awaitUntil(BooleanSupplier condition) {
    long deadline = System.currentTimeMillis() + 15_000;
    while (System.currentTimeMillis() < deadline) {
      if (condition.getAsBoolean()) {
        return true;
      }
      try {
        Thread.sleep(200);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    return condition.getAsBoolean();
  }

  private static InventoryReserved inventoryReserved(UUID orderId) {
    return InventoryReserved.newBuilder()
        .setOrderId(orderId.toString())
        .addItems(ReservedItem.newBuilder().setProductId("prod-1").setQuantity(2).build())
        .build();
  }

  @Test
  @DisplayName("AC-5/7: inventory.reserved → payment.requested 발행, payment.completed → CONFIRMED")
  void happyPathThroughSaga() {
    UUID orderId = placeOrder();
    assertThat(statusOf(orderId)).isEqualTo(OrderStatus.PENDING);

    send("inventory.reserved", orderId.toString(), inventoryReserved(orderId));
    assertThat(awaitUntil(() -> hasOutbox(orderId, "payment.requested"))).isTrue();

    send(
        "payment.completed",
        orderId.toString(),
        PaymentCompleted.newBuilder().setOrderId(orderId.toString()).setAmountMinor(2000L).build());
    assertThat(awaitUntil(() -> statusOf(orderId) == OrderStatus.CONFIRMED)).isTrue();
  }

  @Test
  @DisplayName("AC-9: payment.failed → CANCELLED + order.cancelled 발행 (보상 트리거)")
  void paymentFailedCompensates() {
    UUID orderId = placeOrder();

    send(
        "payment.failed",
        orderId.toString(),
        PaymentFailed.newBuilder().setOrderId(orderId.toString()).setReason("declined").build());

    assertThat(awaitUntil(() -> statusOf(orderId) == OrderStatus.CANCELLED)).isTrue();
    assertThat(hasOutbox(orderId, "order.cancelled")).isTrue();
  }

  @Test
  @DisplayName("AC-8: cancel 유스케이스 → CANCELLED + order.cancelled 적재")
  void userCancel() {
    UUID orderId = placeOrder();

    cancelOrderUseCase.cancel(new CancelOrderCommand(orderId, "changed-mind"));

    assertThat(statusOf(orderId)).isEqualTo(OrderStatus.CANCELLED);
    assertThat(hasOutbox(orderId, "order.cancelled")).isTrue();
  }

  @Test
  @DisplayName("AC-10: 늦은 중복 payment.completed → 상태 불변 (전이 가드)")
  void duplicatePaymentCompletedIgnored() {
    UUID orderId = placeOrder();

    send("inventory.reserved", orderId.toString(), inventoryReserved(orderId));
    assertThat(awaitUntil(() -> hasOutbox(orderId, "payment.requested"))).isTrue();

    PaymentCompleted completed =
        PaymentCompleted.newBuilder().setOrderId(orderId.toString()).setAmountMinor(2000L).build();
    send("payment.completed", orderId.toString(), completed);
    assertThat(awaitUntil(() -> statusOf(orderId) == OrderStatus.CONFIRMED)).isTrue();

    // duplicate delivery must not flip or re-transition the order
    send("payment.completed", orderId.toString(), completed);
    try {
      Thread.sleep(2_000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    assertThat(statusOf(orderId)).isEqualTo(OrderStatus.CONFIRMED);
  }
}
