package com.rodminjo.commerce.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Message;
import com.rodminjo.commerce.events.payment.PaymentRequested;
import com.rodminjo.commerce.payment.adapter.out.persistence.jpa.PaymentJpaRepository;
import com.rodminjo.commerce.payment.adapter.out.persistence.jpa.entity.PaymentJpaEntity;
import com.rodminjo.commerce.payment.domain.model.PaymentStatus;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializerConfig;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.util.List;
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
 * Docker 없는 payment-service 인→아웃 통합 테스트. {@code payment.requested} 발행 후 실제 리스너(Zonky Postgres)가
 * 처리하도록 하고, 저장된 결제 및 outbox 이벤트를 단언. {@code payment.simulate.fail-for-amount} 스위치를 고정 금액에 연결해 단일
 * 컨텍스트에서 성공/실패 경로를 모두 검증.
 */
@SpringBootTest
@EmbeddedKafka(
    topics = {"payment.requested", "payment.completed", "payment.failed"},
    partitions = 1,
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class PaymentProcessIntegrationTest {

  private static final String MOCK_REGISTRY = "mock://payment-test";
  private static final long FAIL_AMOUNT = 13L;
  private static EmbeddedPostgres embeddedPg;

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) throws IOException {
    embeddedPg = EmbeddedPostgres.builder().start();
    String jdbcUrl = embeddedPg.getJdbcUrl("postgres", "postgres") + "&currentSchema=payment_svc";
    registry.add("spring.datasource.url", () -> jdbcUrl);
    registry.add("spring.datasource.username", () -> "postgres");
    registry.add("spring.datasource.password", () -> "postgres");
    registry.add("spring.flyway.create-schemas", () -> "true");
    registry.add("spring.kafka.properties.schema.registry.url", () -> MOCK_REGISTRY);
    registry.add("outbox.relay.poll-interval-ms", () -> "3600000");
    registry.add("payment.simulate.fail-for-amount", () -> String.valueOf(FAIL_AMOUNT));
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

  @Autowired private PaymentJpaRepository paymentJpaRepository;
  @Autowired private OutboxTestRepository outboxRepository;
  @Autowired private EmbeddedKafkaBroker embeddedKafka;

  private void send(String orderId, long amount) {
    PaymentRequested event =
        PaymentRequested.newBuilder()
            .setOrderId(orderId)
            .setAmountMinor(amount)
            .setCurrency("KRW")
            .setIdempotencyKey(orderId)
            .build();
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString());
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaProtobufSerializer.class);
    props.put(KafkaProtobufSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, MOCK_REGISTRY);
    try (KafkaProducer<String, Message> producer = new KafkaProducer<>(props)) {
      producer.send(new ProducerRecord<>("payment.requested", orderId, event)).get();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
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

  private PaymentStatus statusOf(String orderId) {
    List<PaymentJpaEntity> rows = paymentJpaRepository.findByOrderId(orderId);
    return rows.isEmpty() ? null : rows.get(0).getStatus();
  }

  @Test
  @DisplayName("AC-6: payment.requested 수신 → payments COMPLETED + payment.completed 적재")
  void completesPayment() {
    String orderId = "order-pay-ok-1";

    send(orderId, 2000L);

    assertThat(awaitUntil(() -> statusOf(orderId) == PaymentStatus.COMPLETED)).isTrue();
    assertThat(outboxRepository.findByTopic("payment.completed"))
        .anySatisfy(e -> assertThat(e.getAggregateId()).isEqualTo(orderId));
  }

  @Test
  @DisplayName("AC-6: 실패 주입 금액 → payments FAILED + payment.failed 적재")
  void failsPaymentWhenInjected() {
    String orderId = "order-pay-fail-1";

    send(orderId, FAIL_AMOUNT);

    assertThat(awaitUntil(() -> statusOf(orderId) == PaymentStatus.FAILED)).isTrue();
    assertThat(outboxRepository.findByTopic("payment.failed"))
        .anySatisfy(e -> assertThat(e.getAggregateId()).isEqualTo(orderId));
  }
}
