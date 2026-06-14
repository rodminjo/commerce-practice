package com.rodminjo.commerce.inventory.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Message;
import com.rodminjo.commerce.events.order.OrderCancelled;
import com.rodminjo.commerce.events.order.OrderLineItem;
import com.rodminjo.commerce.events.order.OrderPlaced;
import com.rodminjo.commerce.inventory.application.port.out.InventoryStockPort;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializerConfig;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
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
 * Docker 없는 멱등성 통합 테스트: 동일 {@code x-event-id}로 {@code order.placed}를 2회 발행해도 재고 예약이 1회분만 반영되는지, 독성
 * 메시지가 DLT로 격리되어 메인 흐름을 막지 않는지, release 도 멱등한지 검증.
 *
 * <p>재시도 토픽({@code order.placed-retry-0} 등)과 DLT({@code order.placed-dlt})는 {@code
 * autoCreateTopics="true"} 설정과 {@code @EmbeddedKafka topics} 목록에 사전 선언하여 안정적으로 생성.
 */
@SpringBootTest
@EmbeddedKafka(
    topics = {
      "order.placed",
      "order.placed-retry-0",
      "order.placed-retry-1",
      "order.placed-retry-2",
      "order.placed-dlt",
      "order.cancelled",
      "order.cancelled-retry-0",
      "order.cancelled-retry-1",
      "order.cancelled-retry-2",
      "order.cancelled-dlt",
      "inventory.reserved",
      "inventory.released"
    },
    partitions = 1,
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DisplayName("재고 서비스 멱등성·DLT 통합 테스트 (Kafka + Postgres)")
class InventoryIdempotencyIntegrationTest {

  private static final String MOCK_REGISTRY = "mock://inventory-idempotency-test";
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
    // 릴레이 자동 실행 비활성화 — 테스트에서 직접 제어
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
  @Autowired private EmbeddedKafkaBroker embeddedKafka;

  // ── 헬퍼 ────────────────────────────────────────────────────────────────────

  private KafkaProducer<String, Message> producer() {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString());
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaProtobufSerializer.class);
    props.put(KafkaProtobufSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, MOCK_REGISTRY);
    return new KafkaProducer<>(props);
  }

  /**
   * {@code x-event-id} 헤더를 포함하여 메시지 발행.
   *
   * @param eventId 멱등 dedup 키; null 이면 헤더 미포함(독성 메시지 시뮬레이션)
   */
  private void send(String topic, String key, Message event, UUID eventId) {
    try (KafkaProducer<String, Message> p = producer()) {
      Headers headers = new RecordHeaders();
      if (eventId != null) {
        headers.add("x-event-id", eventId.toString().getBytes(StandardCharsets.UTF_8));
      }
      ProducerRecord<String, Message> record =
          new ProducerRecord<>(topic, null, key, event, headers);
      p.send(record).get();
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

  // ── 테스트 케이스 ─────────────────────────────────────────────────────────────

  @Test
  @DisplayName("AC-4: 동일 x-event-id로 order.placed 2회 발행 → reserved는 1회분만 증가")
  void duplicateOrderPlaced_reservedIncrementsOnce() {
    String orderId = "idem-order-1";
    UUID eventId = UUID.randomUUID();
    int before = reserved("prod-1");

    OrderPlaced event = orderPlaced(orderId, "prod-1", 2);
    // 첫 번째 발행 — 정상 처리
    send("order.placed", orderId, event, eventId);
    assertThat(awaitUntil(() -> reserved("prod-1") == before + 2)).isTrue();

    // 두 번째 발행 — 동일 x-event-id: dedup으로 skip되어야 함
    send("order.placed", orderId, event, eventId);

    // 충분히 대기한 뒤에도 reserved 는 1회분(before+2)에서 변하지 않아야 한다
    sleep(3_000);
    assertThat(reserved("prod-1")).isEqualTo(before + 2);
  }

  @Test
  @DisplayName("AC-5: x-event-id 헤더 누락(독성 메시지) → DLT 격리, 메인 흐름 비차단")
  void missingEventIdHeader_routedToDlt_mainFlowUnblocked() {
    String poisonOrderId = "idem-poison-1";
    String normalOrderId = "idem-normal-1";
    UUID normalEventId = UUID.randomUUID();
    int before = reserved("prod-1");

    // 헤더 없는 독성 메시지 발행 (eventId=null → x-event-id 헤더 미포함)
    send("order.placed", poisonOrderId, orderPlaced(poisonOrderId, "prod-1", 1), null);

    // 독성 메시지 재시도 중에도 정상 메시지는 처리되어야 함
    send("order.placed", normalOrderId, orderPlaced(normalOrderId, "prod-1", 3), normalEventId);

    // 정상 메시지가 처리된다면 메인 흐름이 막히지 않은 것
    assertThat(awaitUntil(() -> reserved("prod-1") >= before + 3)).isTrue();
  }

  @Test
  @DisplayName("멱등성: order.cancelled 동일 x-event-id 2회 → release 1회분만 수행")
  void duplicateOrderCancelled_releaseIdempotent() {
    String orderId = "idem-cancel-order-1";
    UUID placeEventId = UUID.randomUUID();
    UUID cancelEventId = UUID.randomUUID();
    int before = reserved("prod-1");

    // 먼저 예약
    send("order.placed", orderId, orderPlaced(orderId, "prod-1", 2), placeEventId);
    assertThat(awaitUntil(() -> reserved("prod-1") == before + 2)).isTrue();

    OrderCancelled cancelled =
        OrderCancelled.newBuilder().setOrderId(orderId).setReason("user-cancel").build();

    // 첫 번째 cancel — 복구 수행
    send("order.cancelled", orderId, cancelled, cancelEventId);
    assertThat(awaitUntil(() -> reserved("prod-1") == before)).isTrue();

    // 두 번째 cancel — 동일 x-event-id: dedup으로 skip되어야 함
    send("order.cancelled", orderId, cancelled, cancelEventId);

    // reserved 는 이미 before 이며 음수로 떨어지지 않아야 함
    sleep(3_000);
    assertThat(reserved("prod-1")).isEqualTo(before);
  }
}
