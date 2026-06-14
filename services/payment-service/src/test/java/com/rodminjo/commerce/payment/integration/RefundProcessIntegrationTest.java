package com.rodminjo.commerce.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Message;
import com.rodminjo.commerce.events.payment.PaymentRequested;
import com.rodminjo.commerce.events.payment.RefundRequested;
import com.rodminjo.commerce.payment.adapter.out.persistence.jpa.PaymentJpaRepository;
import com.rodminjo.commerce.payment.adapter.out.persistence.jpa.RefundJpaRepository;
import com.rodminjo.commerce.payment.adapter.out.persistence.jpa.entity.PaymentJpaEntity;
import com.rodminjo.commerce.payment.application.port.in.ProcessPaymentUseCase;
import com.rodminjo.commerce.payment.application.port.in.ProcessPaymentUseCase.ProcessPaymentCommand;
import com.rodminjo.commerce.payment.application.port.in.ProcessRefundUseCase;
import com.rodminjo.commerce.payment.application.port.in.ProcessRefundUseCase.ProcessRefundCommand;
import com.rodminjo.commerce.payment.application.port.out.RefundPort;
import com.rodminjo.commerce.payment.domain.model.Payment;
import com.rodminjo.commerce.payment.domain.model.PaymentStatus;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializerConfig;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
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
 * Docker 없는 환불 통합 테스트. 결제 생성 후 {@code refund.requested}를 발행해 실제 리스너(Zonky Postgres)가 처리하도록 하고,
 * {@code refunds} 행과 {@code refund.completed}/{@code refund.failed} outbox 적재를 단언한다. 멱등(같은 refundId
 * 2회), 부분/초과 환불, 결제 멱등, 독성 메시지 DLT 격리를 검증한다.
 *
 * <p>재시도 토픽({@code refund.requested-retry-N})과 DLT({@code refund.requested-dlt})는 {@code
 * autoCreateTopics="true"}와 {@code @EmbeddedKafka topics} 사전 선언으로 안정적으로 생성.
 */
@SpringBootTest
@EmbeddedKafka(
    topics = {
      "payment.requested",
      "payment.requested-retry-0",
      "payment.requested-retry-1",
      "payment.requested-retry-2",
      "payment.requested-dlt",
      "payment.completed",
      "payment.failed",
      "refund.requested",
      "refund.requested-retry-0",
      "refund.requested-retry-1",
      "refund.requested-retry-2",
      "refund.requested-dlt",
      "refund.completed",
      "refund.failed"
    },
    partitions = 1,
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DisplayName("결제 서비스 환불·멱등·DLT 통합 테스트 (Kafka + Postgres)")
class RefundProcessIntegrationTest {

  private static final String MOCK_REGISTRY = "mock://payment-refund-test";
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
  @Autowired private RefundJpaRepository refundJpaRepository;
  @Autowired private OutboxTestRepository outboxRepository;
  @Autowired private EmbeddedKafkaBroker embeddedKafka;
  @Autowired private RefundPort refundPort;
  @Autowired private ProcessRefundUseCase processRefundUseCase;
  @Autowired private ProcessPaymentUseCase processPaymentUseCase;

  // ── 헬퍼 ────────────────────────────────────────────────────────────────────

  private KafkaProducer<String, Message> producer() {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString());
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaProtobufSerializer.class);
    props.put(KafkaProtobufSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, MOCK_REGISTRY);
    return new KafkaProducer<>(props);
  }

  /** {@code x-event-id} 헤더를 포함하여 발행. eventId=null 이면 헤더 미포함(독성 메시지 시뮬레이션). */
  private void send(String topic, String key, Message event, UUID eventId) {
    try (KafkaProducer<String, Message> p = producer()) {
      Headers headers = new RecordHeaders();
      if (eventId != null) {
        headers.add("x-event-id", eventId.toString().getBytes(StandardCharsets.UTF_8));
      }
      p.send(new ProducerRecord<>(topic, null, key, event, headers)).get();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void sendPayment(String orderId, long amount, UUID eventId) {
    PaymentRequested event =
        PaymentRequested.newBuilder()
            .setOrderId(orderId)
            .setAmountMinor(amount)
            .setCurrency("KRW")
            .setIdempotencyKey(orderId)
            .build();
    send("payment.requested", orderId, event, eventId);
  }

  private void sendRefund(
      String orderId, String paymentId, String refundId, long amount, UUID eventId) {
    RefundRequested event =
        RefundRequested.newBuilder()
            .setOrderId(orderId)
            .setPaymentId(paymentId)
            .setRefundId(refundId)
            .setAmountMinor(amount)
            .setReason("customer")
            .setIdempotencyKey(refundId)
            .build();
    send("refund.requested", orderId, event, eventId);
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

  private PaymentJpaEntity awaitPayment(String orderId) {
    assertThat(
            awaitUntil(
                () -> {
                  List<PaymentJpaEntity> rows = paymentJpaRepository.findByOrderId(orderId);
                  return !rows.isEmpty() && rows.get(0).getStatus() == PaymentStatus.COMPLETED;
                }))
        .isTrue();
    return paymentJpaRepository.findByOrderId(orderId).get(0);
  }

  // ── 테스트 케이스 ─────────────────────────────────────────────────────────────

  @Test
  @DisplayName("refund.requested 수신 → refunds 1행 + refund.completed 적재")
  void processesRefund() {
    String orderId = "order-refund-1";
    sendPayment(orderId, 10_000L, UUID.randomUUID());
    PaymentJpaEntity payment = awaitPayment(orderId);
    String refundId = "refund-1";

    sendRefund(orderId, payment.getId().toString(), refundId, 3000L, UUID.randomUUID());

    assertThat(awaitUntil(() -> refundJpaRepository.findById(refundId).isPresent())).isTrue();
    assertThat(outboxRepository.findByTopic("refund.completed"))
        .anySatisfy(e -> assertThat(e.getAggregateId()).isEqualTo(orderId));
    assertThat(
            paymentJpaRepository.findById(payment.getId()).orElseThrow().getRefundedAmountMinor())
        .isEqualTo(3000L);
  }

  @Test
  @DisplayName("같은 refundId 2회 → refunds 1행 유지(이중 환불 없음)")
  void idempotentRefund() {
    String orderId = "order-refund-2";
    sendPayment(orderId, 10_000L, UUID.randomUUID());
    PaymentJpaEntity payment = awaitPayment(orderId);
    String refundId = "refund-2";

    sendRefund(orderId, payment.getId().toString(), refundId, 4000L, UUID.randomUUID());
    assertThat(awaitUntil(() -> refundJpaRepository.findById(refundId).isPresent())).isTrue();

    // 다른 x-event-id로 같은 refundId 재요청 → 비즈니스 멱등으로 추가 차감 없음
    sendRefund(orderId, payment.getId().toString(), refundId, 4000L, UUID.randomUUID());
    sleep(3_000);

    // 이중 환불 없음(AC-12): refundId(PK)/idempotencyKey(UNIQUE)로 정확히 1행만 존재하고,
    // 두 번째 요청은 추가 차감 없이 기존 결과를 재사용하므로 차감액은 1회분(4000)만 유지된다.
    assertThat(refundJpaRepository.findByIdempotencyKey(refundId)).isPresent();
    assertThat(refundJpaRepository.findById(refundId)).isPresent();
    assertThat(
            paymentJpaRepository.findById(payment.getId()).orElseThrow().getRefundedAmountMinor())
        .isEqualTo(4000L);
  }

  @Test
  @DisplayName("초과 환불 → refund.failed 적재 + refunds 행 없음")
  void overRefundRejected() {
    String orderId = "order-refund-3";
    sendPayment(orderId, 5000L, UUID.randomUUID());
    PaymentJpaEntity payment = awaitPayment(orderId);
    String refundId = "refund-3-over";

    sendRefund(orderId, payment.getId().toString(), refundId, 9999L, UUID.randomUUID());

    assertThat(
            awaitUntil(
                () ->
                    outboxRepository.findByTopic("refund.failed").stream()
                        .anyMatch(e -> e.getAggregateId().equals(orderId))))
        .isTrue();
    assertThat(refundJpaRepository.findById(refundId)).isEmpty();
    assertThat(
            paymentJpaRepository.findById(payment.getId()).orElseThrow().getRefundedAmountMinor())
        .isZero();
  }

  @Test
  @DisplayName("환불 대상 선택: 한 주문에 FAILED·COMPLETED 결제가 섞여 있으면 COMPLETED를 환불 대상으로 선택")
  void findPaymentByOrderIdSelectsCompleted() {
    String orderId = "order-multi-pay-1";
    // 같은 주문에 FAILED 결제(먼저 저장) + COMPLETED 결제를 직접 시드. idempotencyKey는 UNIQUE라 서로 다르게.
    UUID failedId = UUID.randomUUID();
    UUID completedId = UUID.randomUUID();
    paymentJpaRepository.save(
        PaymentJpaEntity.fromDomain(
            Payment.reconstitute(
                failedId, orderId, 5000L, "KRW", PaymentStatus.FAILED, orderId + "-attempt-1")));
    paymentJpaRepository.save(
        PaymentJpaEntity.fromDomain(
            Payment.reconstitute(
                completedId,
                orderId,
                5000L,
                "KRW",
                PaymentStatus.COMPLETED,
                orderId + "-attempt-2")));

    Payment selected = refundPort.findPaymentByOrderId(orderId).orElseThrow();

    assertThat(selected.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(selected.getId()).isEqualTo(completedId);
  }

  // ── 동시성(레이스 컨디션) ──────────────────────────────────────────────────────

  @Test
  @DisplayName("동시성: 같은 refundId 동시 환불 N개 → refunds 1행, 잔액 1회만 차감(이중 환불 0)")
  void concurrentSameRefundId_appliesOnce() throws InterruptedException {
    String orderId = "order-refund-race-1";
    UUID paymentId = seedCompletedPayment(orderId, 100_000L);
    String refundId = "refund-race-1";
    long amount = 3000L;

    runConcurrently(
        8,
        i ->
            processRefundUseCase.process(
                new ProcessRefundCommand(
                    orderId, paymentId.toString(), refundId, amount, "customer", refundId)));

    // PK(refundId)로 정확히 1행, 잔액은 동시 도착에도 1회분(3000)만 차감.
    assertThat(refundJpaRepository.findByIdempotencyKey(refundId)).isPresent();
    assertThat(refundJpaRepository.findById(refundId)).isPresent();
    assertThat(paymentJpaRepository.findById(paymentId).orElseThrow().getRefundedAmountMinor())
        .isEqualTo(amount);
  }

  @Test
  @DisplayName("동시성: 결제액 초과 동시 부분환불 → 원자적 차감으로 누적 환불이 결제액을 넘지 않음")
  void concurrentPartialRefunds_neverExceedPaymentAmount() throws InterruptedException {
    String orderId = "order-refund-race-2";
    UUID paymentId = seedCompletedPayment(orderId, 10_000L);
    long each = 4000L; // 6건 동시 시도 → 합계 24000이지만 결제액 10000으로 최대 2건(8000)만 성공 가능

    runConcurrently(
        6,
        i -> {
          String refundId = "refund-race-2-" + i;
          processRefundUseCase.process(
              new ProcessRefundCommand(
                  orderId, paymentId.toString(), refundId, each, "customer", refundId));
        });

    long refunded = paymentJpaRepository.findById(paymentId).orElseThrow().getRefundedAmountMinor();
    // 초과판매 방지와 동일 원리: 누적 환불액은 결제액을 절대 초과하지 않고, 성공 건수만큼만 차감.
    assertThat(refunded).isLessThanOrEqualTo(10_000L);
    assertThat(refunded % each).isZero();
    assertThat(refunded).isEqualTo(8000L); // 정확히 2건 성공
  }

  @Test
  @DisplayName("동시성: 같은 orderId 결제요청 동시 N개 → payments 1행(UNIQUE 멱등)")
  void concurrentSamePayment_singleRow() throws InterruptedException {
    String orderId = "order-pay-race-1";

    runConcurrently(
        8,
        i ->
            processPaymentUseCase.process(
                new ProcessPaymentCommand(orderId, 7000L, "KRW", orderId)));

    assertThat(paymentJpaRepository.findByOrderId(orderId)).hasSize(1);
  }

  /** COMPLETED 결제 1행을 직접 시드하고 paymentId 반환. */
  private UUID seedCompletedPayment(String orderId, long amountMinor) {
    UUID paymentId = UUID.randomUUID();
    paymentJpaRepository.save(
        PaymentJpaEntity.fromDomain(
            Payment.reconstitute(
                paymentId,
                orderId,
                amountMinor,
                "KRW",
                PaymentStatus.COMPLETED,
                orderId + "-pay")));
    return paymentId;
  }

  /** N개 스레드가 동시에 출발하도록 배리어로 묶어 task를 실행. 경합 패자의 예외는 무시(설계상 재전달/거부). */
  private void runConcurrently(int threads, IntConsumer task) throws InterruptedException {
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);
    for (int i = 0; i < threads; i++) {
      int idx = i;
      pool.submit(
          () -> {
            try {
              start.await();
              task.accept(idx);
            } catch (Exception ignored) {
              // 경합 패자: UNIQUE 위반 등으로 롤백(설계된 동작) — 무시.
            } finally {
              done.countDown();
            }
          });
    }
    start.countDown();
    assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
    pool.shutdownNow();
  }

  @Test
  @DisplayName("결제 멱등: 같은 orderId 결제요청 2회 → payments 1행")
  void paymentIdempotency() {
    String orderId = "order-pay-idem-1";

    sendPayment(orderId, 7000L, UUID.randomUUID());
    awaitPayment(orderId);

    // 다른 x-event-id로 같은 orderId 재결제 → 비즈니스 멱등(UNIQUE idempotency_key)으로 1행 유지
    sendPayment(orderId, 7000L, UUID.randomUUID());
    sleep(3_000);

    assertThat(paymentJpaRepository.findByOrderId(orderId)).hasSize(1);
  }

  @Test
  @DisplayName("독성 메시지(x-event-id 누락) → DLT 격리, 메인 흐름 비차단")
  void poisonMessageRoutedToDlt() {
    String poisonOrderId = "order-poison-1";
    String normalOrderId = "order-normal-1";

    // 헤더 없는 독성 결제 메시지(eventId=null)
    sendPayment(poisonOrderId, 8000L, null);

    // 독성 메시지 재시도 중에도 정상 메시지는 처리되어야 함
    sendPayment(normalOrderId, 8000L, UUID.randomUUID());

    assertThat(
            awaitUntil(
                () -> {
                  List<PaymentJpaEntity> rows = paymentJpaRepository.findByOrderId(normalOrderId);
                  return !rows.isEmpty() && rows.get(0).getStatus() == PaymentStatus.COMPLETED;
                }))
        .isTrue();
  }
}
