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
 * Verifies partial-failure isolation inside a single {@code publishBatch()} transaction: when one
 * event in the batch fails to publish, the events that already succeeded are NOT rolled back.
 *
 * <p>The relay catches per event inside the loop ({@code OutboxRelay#publishBatch}), so a failure
 * increments that one row's {@code attempts} and leaves it PENDING for retry, while the exception
 * never escapes the method — the transaction commits normally and the successful row stays
 * PUBLISHED. This test forces exactly that mix: a valid {@code OrderPlaced} row (registered type)
 * alongside a row whose {@code eventType} is not in the {@code OutboxTypeRegistry} (so {@code
 * registry.get()} throws). Both sit in the same batch (batchSize defaults to 100).
 *
 * <p>Lives in its own class (own context + embedded Postgres + Kafka broker) so its publish does
 * not pollute the exact topic counts asserted by {@link OutboxRelayIntegrationTest}. Docker-free:
 * Zonky embedded Postgres + {@code @EmbeddedKafka}.
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
    // Disable scheduled polling — drive publishBatch() manually.
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
  @DisplayName(
      "one bad event in a batch does NOT roll back the published one: A=PUBLISHED+sent, B=PENDING attempts=1")
  void partialFailure_doesNotRollBackPublishedEvent() {
    Instant now = Instant.parse("2024-01-01T00:00:00Z");

    // A: valid, registered type → publishes successfully.
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

    // B: eventType NOT registered → registry.get() throws → caught per-event → stays PENDING.
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

    // when: one batch processes both
    int published = outboxRelay.publishBatch();

    // then: only the good one counted as published
    assertThat(published).isEqualTo(1);

    // A committed as PUBLISHED — the failure of B did NOT roll it back
    OutboxEvent good = outboxRepository.findById(goodId).orElseThrow();
    assertThat(good.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    assertThat(good.getPublishedAt()).isNotNull();
    assertThat(good.getAttempts()).isZero();

    // B stayed PENDING with attempts incremented — eligible for the next poll's retry
    OutboxEvent bad = outboxRepository.findById(badId).orElseThrow();
    assertThat(bad.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(bad.getPublishedAt()).isNull();
    assertThat(bad.getAttempts()).isEqualTo(1);

    // and A genuinely reached Kafka (exactly one record, the good one)
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
