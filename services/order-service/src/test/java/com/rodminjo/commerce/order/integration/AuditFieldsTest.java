package com.rodminjo.commerce.order.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.rodminjo.commerce.common.infra.persistence.SecurityAuditorAware;
import com.rodminjo.commerce.common.outbox.appender.OutboxAppender;
import com.rodminjo.commerce.order.adapter.out.persistence.jpa.OrderItemJpaRepository;
import com.rodminjo.commerce.order.adapter.out.persistence.jpa.OrderJpaRepository;
import com.rodminjo.commerce.order.adapter.out.persistence.jpa.entity.OrderItemJpaEntity;
import com.rodminjo.commerce.order.adapter.out.persistence.jpa.entity.OrderJpaEntity;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * {@code BaseEntity} JPA 감사 필드 검증: 삽입 시 생성/수정 타임스탬프와 액터가 자동 채워지며, 인증 액터 없을 때(SecurityContext 미설정)
 * {@code "SYSTEM"}으로 폴백. Docker 불필요: Zonky 임베디드 Postgres 사용.
 */
@SpringBootTest
@EmbeddedKafka(
    topics = {"order.placed", "order.cancelled"},
    partitions = 1,
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class AuditFieldsTest {

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

  // 주문 커밋 허용용 No-op; 감사 컬럼만 검증.
  @MockitoBean private OutboxAppender outboxAppender;

  @Autowired private PlaceOrderUseCase placeOrderUseCase;

  @Autowired private OrderJpaRepository orderJpaRepository;

  @Autowired private OrderItemJpaRepository orderItemJpaRepository;

  @Test
  @DisplayName("삽입 시 감사 필드 자동 채움; 비인증 시 액터 SYSTEM 폴백")
  void auditFieldsPopulatedWithSystemFallback() {
    PlaceOrderCommand cmd =
        new PlaceOrderCommand(
            "customer-audit-1", List.of(new OrderItemCommand("prod-audit", 2, 1500L)), "USD");

    UUID orderId = placeOrderUseCase.place(cmd).orderId();

    OrderJpaEntity order = orderJpaRepository.findById(orderId).orElseThrow();
    // 도메인 소유 비즈니스 생성 시각 — 영속화 후 복원됨.
    assertThat(order.getCreatedAt()).isNotNull();
    // 감사 컬럼 자동 채움; 비인증 시 액터는 SYSTEM으로 폴백.
    assertThat(order.getAuditCreatedAt()).isNotNull();
    assertThat(order.getAuditUpdatedAt()).isNotNull();
    assertThat(order.getAuditCreatedBy()).isEqualTo(SecurityAuditorAware.SYSTEM);
    assertThat(order.getAuditUpdatedBy()).isEqualTo(SecurityAuditorAware.SYSTEM);

    List<OrderItemJpaEntity> items = orderItemJpaRepository.findByOrderId(orderId);
    assertThat(items).hasSize(1);
    OrderItemJpaEntity item = items.get(0);
    assertThat(item.getAuditCreatedAt()).isNotNull();
    assertThat(item.getAuditUpdatedAt()).isNotNull();
    assertThat(item.getAuditCreatedBy()).isEqualTo(SecurityAuditorAware.SYSTEM);
    assertThat(item.getAuditUpdatedBy()).isEqualTo(SecurityAuditorAware.SYSTEM);
  }
}
