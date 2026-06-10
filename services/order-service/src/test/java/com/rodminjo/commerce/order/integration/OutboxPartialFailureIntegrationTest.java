package com.rodminjo.commerce.order.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.rodminjo.commerce.common.outbox.entity.OutboxEvent;
import com.rodminjo.commerce.common.outbox.entity.OutboxStatus;
import com.rodminjo.commerce.common.outbox.relay.OutboxRelay;
import com.rodminjo.commerce.events.order.OrderPlaced;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializerConfig;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
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
 * 단일 {@code publishBatch()} 트랜잭션 내 부분 실패 격리 검증: 배치에서 이벤트 1개가 발행 실패해도 이미 성공한 이벤트는 롤백되지 않음.
 *
 * <p>릴레이가 루프 내 이벤트별로 예외를 포착({@code OutboxRelay#publishBatch})하므로 실패 시 해당 행의 {@code attempts}만 증가하고
 * PENDING 상태로 재시도 대기, 예외가 메서드를 벗어나지 않아 트랜잭션은 정상 커밋되고 성공 행은 PUBLISHED 유지. 이 테스트는 유효한 {@code
 * OrderPlaced} 행(등록된 타입)과 {@code OutboxTypeRegistry}에 없는 {@code eventType} 행({@code registry.get()}
 * 예외 발생)을 동일 배치(batchSize 기본 100)에 함께 구성.
 *
 * <p>독립 클래스(자체 컨텍스트 + 임베디드 Postgres + Kafka)로 분리하여 발행이 {@link OutboxRelayIntegrationTest}의 정확한 토픽
 * 카운트 단언을 오염시키지 않도록 방지. Docker 불필요: Zonky 임베디드 Postgres + {@code @EmbeddedKafka}.
 */
@SpringBootTest
@EmbeddedKafka(
    topics = {"order.placed", "order.cancelled"},
    partitions = 1,
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class OutboxPartialFailureIntegrationTest {

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
    // 스케줄 폴링 비활성화 — publishBatch()를 수동 구동.
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

  @Autowired private OutboxRelay outboxRelay;

  @Autowired private OutboxTestRepository outboxRepository;

  @Autowired private EmbeddedKafkaBroker embeddedKafka;

  @Test
  @DisplayName("배치 내 불량 이벤트 1개가 발행된 이벤트를 롤백하지 않음: A=PUBLISHED+전송, B=PENDING attempts=1")
  void partialFailure_doesNotRollBackPublishedEvent() {
    Instant now = Instant.parse("2024-01-01T00:00:00Z");

    // A: 유효하고 등록된 타입 → 발행 성공.
    UUID goodId = UUID.randomUUID();
    OrderPlaced payload =
        OrderPlaced.newBuilder()
            .setOrderId(goodId.toString())
            .setCustomerId("customer-partial-good")
            .setTotalAmountMinor(1_000L)
            .setCurrency("KRW")
            .build();
    outboxRepository.save(
        OutboxEvent.pending(
            goodId,
            "Order",
            goodId.toString(),
            "commerce.events.order.OrderPlaced", // registered in OutboxConfig
            "order.placed",
            goodId.toString(),
            payload.toByteArray(),
            now));

    // B: 미등록 eventType → registry.get() 예외 → 이벤트별 포착 → PENDING 유지.
    UUID badId = UUID.randomUUID();
    outboxRepository.save(
        OutboxEvent.pending(
            badId,
            "Order",
            badId.toString(),
            "commerce.events.order.NotRegistered", // absent from the registry
            "order.placed",
            badId.toString(),
            new byte[] {1},
            now.plusSeconds(1)));

    // when: 단일 배치로 둘 다 처리
    int published = outboxRelay.publishBatch();

    // then: 정상 이벤트만 발행 카운트에 포함
    assertThat(published).isEqualTo(1);

    // A는 PUBLISHED로 커밋 — B 실패가 A를 롤백하지 않음
    OutboxEvent good = outboxRepository.findById(goodId).orElseThrow();
    assertThat(good.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    assertThat(good.getPublishedAt()).isNotNull();
    assertThat(good.getAttempts()).isZero();

    // B는 attempts 증가 후 PENDING 유지 — 다음 폴 재시도 대상
    OutboxEvent bad = outboxRepository.findById(badId).orElseThrow();
    assertThat(bad.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(bad.getPublishedAt()).isNull();
    assertThat(bad.getAttempts()).isEqualTo(1);

    // A가 Kafka에 실제 도달(정확히 레코드 1개, 정상 이벤트)
    List<ConsumerRecord<String, OrderPlaced>> records = consumeOrderPlaced();
    assertThat(records).hasSize(1);
    assertThat(records.get(0).key()).isEqualTo(goodId.toString());
    assertThat(records.get(0).value().getOrderId()).isEqualTo(goodId.toString());
  }

  private List<ConsumerRecord<String, OrderPlaced>> consumeOrderPlaced() {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaProtobufDeserializer.class);
    props.put(KafkaProtobufDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "mock://order-test");
    props.put(
        KafkaProtobufDeserializerConfig.SPECIFIC_PROTOBUF_VALUE_TYPE, OrderPlaced.class.getName());

    List<ConsumerRecord<String, OrderPlaced>> records = new ArrayList<>();
    try (KafkaConsumer<String, OrderPlaced> consumer = new KafkaConsumer<>(props)) {
      consumer.subscribe(List.of("order.placed"));
      long deadline = System.currentTimeMillis() + 10_000;
      while (records.isEmpty() && System.currentTimeMillis() < deadline) {
        consumer.poll(Duration.ofMillis(500)).forEach(records::add);
      }
    }
    return records;
  }
}
