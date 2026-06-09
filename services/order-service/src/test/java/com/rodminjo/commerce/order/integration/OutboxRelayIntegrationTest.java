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
 * Docker-free integration test of the Transactional Outbox relay path.
 *
 * <p>Infra: real embedded PostgreSQL (Zonky — runs an actual postgres binary, no Docker) so the
 * {@code FOR UPDATE SKIP LOCKED} outbox query and Postgres-specific DDL are genuinely exercised,
 * plus in-JVM Kafka via {@code @EmbeddedKafka}. Schema Registry is the Confluent {@code mock://}
 * in-memory registry shared by producer (relay) and the test consumer.
 *
 * <p>Verification goes through real repositories/mappers — JPA ({@link OutboxTestRepository}) for
 * simple reads and MyBatis ({@link GetOrderUseCase}) for the order read model — never raw SQL.
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
    // Disable scheduled polling — control publishBatch() manually in tests
    registry.add("outbox.relay.poll-interval-ms", () -> "3600000");
    // Avoid Keycloak issuer lookup during tests
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
  @DisplayName(
      "A: placeOrder → outbox PENDING → publishBatch → Kafka message → idempotent 2nd call")
  void happyPath_and_idempotent() {
    PlaceOrderCommand cmd =
        new PlaceOrderCommand(
            "customer-integration-1", List.of(new OrderItemCommand("prod-1", 2, 500L)), "KRW");

    UUID orderId = placeOrderUseCase.place(cmd).orderId();

    // outbox: exactly one PENDING row with the right metadata (JPA repository — simple read)
    List<OutboxEvent> events = outboxRepository.findByAggregateId(orderId.toString());
    assertThat(events).hasSize(1);
    OutboxEvent event = events.get(0);
    assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(event.getEventType()).isEqualTo("commerce.events.order.OrderPlaced");
    assertThat(event.getPartitionKey()).isEqualTo(orderId.toString());

    // read model via MyBatis (order + items join)
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

    // publish batch — expect 1 published
    int published = outboxRelay.publishBatch();
    assertThat(published).isEqualTo(1);

    // outbox row is now PUBLISHED with published_at set
    OutboxEvent afterPublish = outboxRepository.findByAggregateId(orderId.toString()).get(0);
    assertThat(afterPublish.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    assertThat(afterPublish.getPublishedAt()).isNotNull();

    // consume from Kafka
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

    // idempotent: 2nd publishBatch returns 0
    int secondPublish = outboxRelay.publishBatch();
    assertThat(secondPublish).isEqualTo(0);
  }
}
