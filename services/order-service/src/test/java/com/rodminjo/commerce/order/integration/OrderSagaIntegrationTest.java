package com.rodminjo.commerce.order.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Message;
import com.rodminjo.commerce.events.inventory.InventoryReserved;
import com.rodminjo.commerce.events.inventory.ReservedItem;
import com.rodminjo.commerce.events.payment.PaymentCompleted;
import com.rodminjo.commerce.events.payment.PaymentFailed;
import com.rodminjo.commerce.events.payment.RefundCompleted;
import com.rodminjo.commerce.order.adapter.out.persistence.jpa.OrderJpaRepository;
import com.rodminjo.commerce.order.application.port.in.CancelOrderUseCase;
import com.rodminjo.commerce.order.application.port.in.CancelOrderUseCase.CancelOrderCommand;
import com.rodminjo.commerce.order.application.port.in.PlaceOrderUseCase;
import com.rodminjo.commerce.order.application.port.in.PlaceOrderUseCase.PlaceOrderCommand;
import com.rodminjo.commerce.order.application.port.in.PlaceOrderUseCase.PlaceOrderCommand.OrderItemCommand;
import com.rodminjo.commerce.order.application.port.in.RefundOrderUseCase;
import com.rodminjo.commerce.order.application.port.in.RefundOrderUseCase.RefundOrderCommand;
import com.rodminjo.commerce.order.domain.model.OrderStatus;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializerConfig;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
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
 * Order Saga 오케스트레이터 인→아웃 통합 테스트(Docker 불필요). 인바운드 Saga 이벤트(inventory.reserved / payment.completed
 * / payment.failed) 및 취소 유스케이스를 실제 임베디드 Postgres(Zonky) + 인-JVM Kafka로 구동하여 결과 상태 전이 및 outbox에 적재된
 * 보상/전진 이벤트를 검증.
 */
@SpringBootTest
@EmbeddedKafka(
    topics = {
      "order.placed",
      "order.cancelled",
      "payment.requested",
      "inventory.reserved",
      "payment.completed",
      "payment.completed-retry-0",
      "payment.completed-retry-1",
      "payment.completed-retry-2",
      "payment.completed-dlt",
      "payment.failed",
      "refund.requested",
      "refund.completed",
      "refund.failed"
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
  @Autowired private RefundOrderUseCase refundOrderUseCase;
  @Autowired private OrderJpaRepository orderJpaRepository;
  @Autowired private OutboxTestRepository outboxRepository;
  @Autowired private EmbeddedKafkaBroker embeddedKafka;

  private UUID placeOrder() {
    PlaceOrderCommand cmd =
        new PlaceOrderCommand(
            "customer-saga", List.of(new OrderItemCommand("prod-1", 2, 1000L)), "KRW");
    return placeOrderUseCase.place(cmd).orderId();
  }

  /** 신규 {@code x-event-id}(랜덤)로 발행 — 매번 다른 메시지로 취급. */
  private void send(String topic, String key, Message event) {
    send(topic, key, event, UUID.randomUUID());
  }

  /**
   * 지정한 {@code x-event-id}로 발행. 멱등 컨슈머가 해당 헤더를 dedup 키로 요구하므로 테스트도 항상 포함한다. 같은 eventId로 두 번 발행하면
   * dedup으로 두 번째는 skip되어야 한다.
   */
  private void send(String topic, String key, Message event, UUID eventId) {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString());
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaProtobufSerializer.class);
    props.put(KafkaProtobufSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, MOCK_REGISTRY);
    RecordHeaders headers = new RecordHeaders();
    headers.add("x-event-id", eventId.toString().getBytes(StandardCharsets.UTF_8));
    try (KafkaProducer<String, Message> producer = new KafkaProducer<>(props)) {
      producer.send(new ProducerRecord<>(topic, null, key, event, headers)).get();
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

    // 중복 전달은 주문 상태를 변경하거나 재전이시키지 않아야 함
    send("payment.completed", orderId.toString(), completed);
    try {
      Thread.sleep(2_000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    assertThat(statusOf(orderId)).isEqualTo(OrderStatus.CONFIRMED);
  }

  /** CONFIRMED 상태까지 주문을 전진시키는 헬퍼(saga happy path). */
  private UUID confirmedOrder() {
    UUID orderId = placeOrder();
    send("inventory.reserved", orderId.toString(), inventoryReserved(orderId));
    assertThat(awaitUntil(() -> hasOutbox(orderId, "payment.requested"))).isTrue();
    send(
        "payment.completed",
        orderId.toString(),
        PaymentCompleted.newBuilder().setOrderId(orderId.toString()).setAmountMinor(2000L).build());
    assertThat(awaitUntil(() -> statusOf(orderId) == OrderStatus.CONFIRMED)).isTrue();
    return orderId;
  }

  @Test
  @DisplayName("환불 요청: POST /refund 유스케이스 → REFUNDED + refund.requested 적재")
  void refundUseCasePublishesRefundRequested() {
    UUID orderId = confirmedOrder();

    refundOrderUseCase.refund(new RefundOrderCommand(orderId, null, "customer-request"));

    assertThat(statusOf(orderId)).isEqualTo(OrderStatus.REFUNDED);
    assertThat(hasOutbox(orderId, "refund.requested")).isTrue();
  }

  @Test
  @DisplayName("환불 완료 saga: refund.completed 수신 → REFUNDED 확정")
  void refundCompletedConfirmsRefund() {
    UUID orderId = confirmedOrder();

    send(
        "refund.completed",
        orderId.toString(),
        RefundCompleted.newBuilder()
            .setOrderId(orderId.toString())
            .setRefundId(orderId + "-refund")
            .setRefundedAmountMinor(2000L)
            .build());

    assertThat(awaitUntil(() -> statusOf(orderId) == OrderStatus.REFUNDED)).isTrue();
  }

  @Test
  @DisplayName("보상 심화: CONFIRMED 주문 cancel → order.cancelled + refund.requested 동시 적재")
  void cancelConfirmedOrderCompensatesWithRefund() {
    UUID orderId = confirmedOrder();

    cancelOrderUseCase.cancel(new CancelOrderCommand(orderId, "out-of-stock"));

    assertThat(statusOf(orderId)).isEqualTo(OrderStatus.CANCELLED);
    assertThat(hasOutbox(orderId, "order.cancelled")).isTrue();
    assertThat(hasOutbox(orderId, "refund.requested")).isTrue();
  }

  @Test
  @DisplayName("멱등: 동일 x-event-id로 refund.completed 2회 → 상태 불변(REFUNDED)")
  void duplicateRefundCompletedDeduped() {
    UUID orderId = confirmedOrder();
    UUID eventId = UUID.randomUUID();
    RefundCompleted completed =
        RefundCompleted.newBuilder()
            .setOrderId(orderId.toString())
            .setRefundId(orderId + "-refund")
            .setRefundedAmountMinor(2000L)
            .build();

    send("refund.completed", orderId.toString(), completed, eventId);
    assertThat(awaitUntil(() -> statusOf(orderId) == OrderStatus.REFUNDED)).isTrue();

    // 동일 x-event-id 재전달 → dedup으로 skip, 상태 불변
    send("refund.completed", orderId.toString(), completed, eventId);
    try {
      Thread.sleep(2_000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    assertThat(statusOf(orderId)).isEqualTo(OrderStatus.REFUNDED);
  }

  @Test
  @DisplayName("AC-6: 독성 payment.completed(x-event-id 누락) → DLT 격리, 다른 주문 정상 처리(메인 흐름 비차단)")
  void poisonSagaEventRoutedToDltDoesNotBlockMainFlow() {
    UUID poisonOrder = placeOrder();
    UUID normalOrder = placeOrder();

    // 독성: x-event-id 헤더 없는 payment.completed → EventIdHeader.parse 예외 → 재시도 후 DLT 격리
    sendWithoutHeader(
        "payment.completed",
        poisonOrder.toString(),
        PaymentCompleted.newBuilder()
            .setOrderId(poisonOrder.toString())
            .setAmountMinor(2000L)
            .build());

    // 독성 메시지가 재시도/DLT로 처리되는 동안에도 정상 주문은 CONFIRMED 되어야 함
    send(
        "payment.completed",
        normalOrder.toString(),
        PaymentCompleted.newBuilder()
            .setOrderId(normalOrder.toString())
            .setAmountMinor(2000L)
            .build());

    assertThat(awaitUntil(() -> statusOf(normalOrder) == OrderStatus.CONFIRMED)).isTrue();
    // 독성 주문은 전이되지 않고 PENDING 유지(부수효과 없이 DLT로 격리)
    assertThat(statusOf(poisonOrder)).isEqualTo(OrderStatus.PENDING);
  }

  /** x-event-id 헤더 없이 발행(독성 메시지 시뮬레이션). 멱등 컨슈머가 헤더를 요구하므로 처리 실패 → 재시도/DLT. */
  private void sendWithoutHeader(String topic, String key, Message event) {
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
}
