package com.rodminjo.commerce.order.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

import com.rodminjo.commerce.common.outbox.appender.OutboxAppender;
import com.rodminjo.commerce.order.adapter.out.persistence.jpa.OrderJpaRepository;
import com.rodminjo.commerce.order.application.port.in.PlaceOrderUseCase;
import com.rodminjo.commerce.order.application.port.in.PlaceOrderUseCase.PlaceOrderCommand;
import com.rodminjo.commerce.order.application.port.in.PlaceOrderUseCase.PlaceOrderCommand.OrderItemCommand;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 이중 쓰기 원자성 보장 검증: outbox 적재 실패 시 주문 INSERT도 함께 롤백(두 쓰기가 하나의 트랜잭션 공유). Docker 불필요: Zonky 임베디드
 * Postgres. 검증은 raw SQL이 아닌 JPA 리포지토리를 통해 수행.
 */
@SpringBootTest
@EmbeddedKafka(
    topics = {"order.placed", "order.cancelled"},
    partitions = 1,
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class OutboxRollbackTest {

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

  @MockitoBean private OutboxAppender outboxAppender;

  @Autowired private PlaceOrderUseCase placeOrderUseCase;

  @Autowired private OrderJpaRepository orderJpaRepository;

  @Autowired private OutboxTestRepository outboxRepository;

  @Test
  @DisplayName("B: OutboxAppender 예외 → 트랜잭션 롤백 → 주문 및 outbox 행 미적재")
  void rollback_onOutboxFailure() {
    doThrow(new RuntimeException("simulated outbox failure"))
        .when(outboxAppender)
        .append(anyString(), anyString(), anyString(), anyString(), any());

    PlaceOrderCommand cmd =
        new PlaceOrderCommand(
            "customer-rollback-1", List.of(new OrderItemCommand("prod-rollback", 1, 1000L)), "USD");

    assertThatThrownBy(() -> placeOrderUseCase.place(cmd)).isInstanceOf(RuntimeException.class);

    // 트랜잭션 롤백 — 주문과 outbox 행 모두 미영속화.
    assertThat(orderJpaRepository.countByCustomerId("customer-rollback-1")).isZero();
    assertThat(outboxRepository.countByAggregateType("Order")).isZero();
  }
}
