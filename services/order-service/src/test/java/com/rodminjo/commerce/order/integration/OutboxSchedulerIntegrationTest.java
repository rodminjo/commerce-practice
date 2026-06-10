package com.rodminjo.commerce.order.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.rodminjo.commerce.common.outbox.entity.OutboxEvent;
import com.rodminjo.commerce.common.outbox.entity.OutboxStatus;
import com.rodminjo.commerce.common.outbox.relay.OutboxRelayScheduler;
import com.rodminjo.commerce.order.application.port.in.PlaceOrderUseCase;
import com.rodminjo.commerce.order.application.port.in.PlaceOrderUseCase.PlaceOrderCommand;
import com.rodminjo.commerce.order.application.port.in.PlaceOrderUseCase.PlaceOrderCommand.OrderItemCommand;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * outbox 릴레이 자기 호출 버그 회귀 방지 테스트. 과거 {@code OutboxRelay.poll()}이 동일 빈의 {@code this.publishBatch()}를
 * 호출하여 Spring 프록시를 우회, 트랜잭션 미실행, {@code markPublished()} 미플러시, 행이 PENDING 유지 — 매 폴마다 재발행. 스케줄 트리거가
 * 현재 {@link OutboxRelayScheduler}(별도 빈)에 위치하여 {@code poll()} 호출이 프록시를 경유하고 {@code @Transactional}
 * 어드바이스가 적용됨.
 *
 * <p>독립 클래스(자체 컨텍스트 + 임베디드 Postgres + Kafka)로 분리하여 추가 발행이 {@code OutboxRelayIntegrationTest}의 정확한
 * {@code order.placed} 토픽 카운트 단언을 오염시키지 않도록 방지. Docker 불필요: Zonky 임베디드 Postgres +
 * {@code @EmbeddedKafka}.
 */
@SpringBootTest
@EmbeddedKafka(
    topics = {"order.placed", "order.cancelled"},
    partitions = 1,
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class OutboxSchedulerIntegrationTest {

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
    // 실제 스케줄 주기 비활성화 — poll()을 수동 구동.
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

  @Autowired private OutboxRelayScheduler outboxRelayScheduler;

  @Autowired private OutboxTestRepository outboxRepository;

  @Test
  @DisplayName("scheduler.poll()이 프록시 경유 → @Transactional 적용, 행이 PUBLISHED로 마킹됨")
  void scheduledPoll_appliesTransaction_andMarksPublished() {
    PlaceOrderCommand cmd =
        new PlaceOrderCommand(
            "customer-scheduler-1", List.of(new OrderItemCommand("prod-1", 1, 700L)), "KRW");
    UUID orderId = placeOrderUseCase.place(cmd).orderId();

    // sanity: place()의 자체 트랜잭션에서 PENDING으로 적재됨
    assertThat(outboxRepository.findByAggregateId(orderId.toString()))
        .singleElement()
        .satisfies(e -> assertThat(e.getStatus()).isEqualTo(OutboxStatus.PENDING));

    // 프로덕션 경로: 스케줄러 빈 → 릴레이 빈 → 프록시 → @Transactional publishBatch()
    outboxRelayScheduler.poll();

    // 구 자기 호출 방식이었다면 여기서 행이 여전히 PENDING 상태일 것
    OutboxEvent afterPoll = outboxRepository.findByAggregateId(orderId.toString()).get(0);
    assertThat(afterPoll.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    assertThat(afterPoll.getPublishedAt()).isNotNull();
  }
}
