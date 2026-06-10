package com.rodminjo.commerce.order.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.rodminjo.commerce.common.outbox.entity.OutboxEvent;
import com.rodminjo.commerce.common.outbox.entity.OutboxStatus;
import com.rodminjo.commerce.events.order.OrderPlaced;
import com.rodminjo.commerce.order.application.port.in.GetOrderUseCase;
import com.rodminjo.commerce.order.application.port.in.GetOrderUseCase.OrderView;
import com.rodminjo.commerce.order.application.port.in.PlaceOrderUseCase;
import com.rodminjo.commerce.order.application.port.in.PlaceOrderUseCase.PlaceOrderCommand;
import com.rodminjo.commerce.order.application.port.in.PlaceOrderUseCase.PlaceOrderCommand.OrderItemCommand;
import com.rodminjo.commerce.order.domain.model.OrderStatus;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializerConfig;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
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
 * Transactional Outbox 릴레이 경로 통합 테스트(Docker 불필요).
 *
 * <p>인프라: 실제 임베디드 PostgreSQL(Zonky — 실제 postgres 바이너리 실행)로 {@code FOR UPDATE SKIP LOCKED} outbox 쿼리
 * 및 Postgres 전용 DDL 실제 검증 + {@code @EmbeddedKafka}로 인-JVM Kafka 사용. Schema Registry는 Confluent
 * {@code mock://} 인메모리 레지스트리를 프로듀서(릴레이)와 테스트 컨슈머가 공유.
 *
 * <p>검증은 실제 리포지토리/매퍼 사용 — 단순 읽기는 JPA({@link OutboxTestRepository}), 주문 읽기 모델은 MyBatis({@link
 * GetOrderUseCase}) — raw SQL 사용 안 함.
 */
@SpringBootTest
@EmbeddedKafka(
    topics = {"order.placed", "order.cancelled"},
    partitions = 1,
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class OutboxRelayIntegrationTest {

  private static EmbeddedPostgres embeddedPg;

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) throws IOException {
    embeddedPg = EmbeddedPostgres.builder().start();
    String jdbcUrl = embeddedPg.getJdbcUrl("postgres", "postgres") + "&currentSchema=order_svc";
    registry.add("spring.datasource.url", () -> jdbcUrl);
    registry.add("spring.datasource.username", () -> "postgres");
    registry.add("spring.datasource.password", () -> "postgres");
    registry.add("spring.flyway.create-schemas", () -> "true");
    registry.add("spring.kafka.properties.schema.registry.url", () -> "mock://order-test");
    // 스케줄 폴링 비활성화 — 테스트에서 publishBatch()를 수동 제어
    registry.add("outbox.relay.poll-interval-ms", () -> "3600000");
    // 테스트 중 Keycloak issuer 조회 방지
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

  @Autowired private GetOrderUseCase getOrderUseCase;

  @Autowired private com.rodminjo.commerce.common.outbox.relay.OutboxRelay outboxRelay;

  @Autowired private OutboxTestRepository outboxRepository;

  @Autowired private EmbeddedKafkaBroker embeddedKafka;

  @Test
  @DisplayName("A: placeOrder → outbox PENDING → publishBatch → Kafka 메시지 전달 → 2차 호출 멱등성")
  void happyPath_and_idempotent() {
    PlaceOrderCommand cmd =
        new PlaceOrderCommand(
            "customer-integration-1", List.of(new OrderItemCommand("prod-1", 2, 500L)), "KRW");

    UUID orderId = placeOrderUseCase.place(cmd).orderId();

    // outbox: 올바른 메타데이터를 가진 PENDING 행 정확히 1개(JPA 리포지토리 — 단순 읽기)
    List<OutboxEvent> events = outboxRepository.findByAggregateId(orderId.toString());
    assertThat(events).hasSize(1);
    OutboxEvent event = events.get(0);
    assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(event.getEventType()).isEqualTo("commerce.events.order.OrderPlaced");
    assertThat(event.getPartitionKey()).isEqualTo(orderId.toString());

    // MyBatis로 읽기 모델 검증(주문 + 항목 조인)
    OrderView view = getOrderUseCase.getOrder(orderId);
    assertThat(view.status()).isEqualTo(OrderStatus.PENDING);
    assertThat(view.currency()).isEqualTo("KRW");
    assertThat(view.totalAmountMinor()).isEqualTo(1_000L); // 2 * 500
    assertThat(view.items())
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.productId()).isEqualTo("prod-1");
              assertThat(item.quantity()).isEqualTo(2);
              assertThat(item.unitPriceMinor()).isEqualTo(500L);
            });

    // 배치 발행 — 1건 발행 기대
    int published = outboxRelay.publishBatch();
    assertThat(published).isEqualTo(1);

    // outbox 행이 published_at 설정과 함께 PUBLISHED 상태로 전환
    OutboxEvent afterPublish = outboxRepository.findByAggregateId(orderId.toString()).get(0);
    assertThat(afterPublish.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    assertThat(afterPublish.getPublishedAt()).isNotNull();

    // Kafka에서 소비
    Properties consumerProps = new Properties();
    consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString());
    consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID());
    consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    consumerProps.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaProtobufDeserializer.class);
    consumerProps.put(
        KafkaProtobufDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "mock://order-test");
    consumerProps.put(
        KafkaProtobufDeserializerConfig.SPECIFIC_PROTOBUF_VALUE_TYPE, OrderPlaced.class.getName());

    List<ConsumerRecord<String, OrderPlaced>> records = new ArrayList<>();
    try (KafkaConsumer<String, OrderPlaced> consumer = new KafkaConsumer<>(consumerProps)) {
      consumer.subscribe(List.of("order.placed"));
      long deadline = System.currentTimeMillis() + 10_000;
      while (records.isEmpty() && System.currentTimeMillis() < deadline) {
        consumer.poll(Duration.ofMillis(500)).forEach(records::add);
      }
    }

    assertThat(records).hasSize(1);
    ConsumerRecord<String, OrderPlaced> record = records.get(0);
    assertThat(record.key()).isEqualTo(orderId.toString());
    assertThat(record.value().getOrderId()).isEqualTo(orderId.toString());

    // 멱등성: 2차 publishBatch는 0 반환
    int secondPublish = outboxRelay.publishBatch();
    assertThat(secondPublish).isEqualTo(0);
  }
}
