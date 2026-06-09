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
 * Verifies JPA auditing on {@code BaseEntity}: created/updated timestamps and actors are filled
 * automatically on insert, falling back to {@code "SYSTEM"} when there is no authenticated actor
 * (this test runs without a SecurityContext). Docker-free: real embedded Postgres (Zonky).
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

  // No-op so the order commits; we only care about audit columns here.
  @MockitoBean private OutboxAppender outboxAppender;

  @Autowired private PlaceOrderUseCase placeOrderUseCase;

  @Autowired private OrderJpaRepository orderJpaRepository;

  @Autowired private OrderItemJpaRepository orderItemJpaRepository;

  @Test
  @DisplayName(
      "Audit fields auto-populate on insert; actor falls back to SYSTEM when unauthenticated")
  void auditFieldsPopulatedWithSystemFallback() {
    PlaceOrderCommand cmd =
        new PlaceOrderCommand(
            "customer-audit-1", List.of(new OrderItemCommand("prod-audit", 2, 1500L)), "USD");

    UUID orderId = placeOrderUseCase.place(cmd).orderId();

    OrderJpaEntity order = orderJpaRepository.findById(orderId).orElseThrow();
    // Business creation time (domain-owned) is persisted and restored.
    assertThat(order.getCreatedAt()).isNotNull();
    // Audit columns auto-populate; actor falls back to SYSTEM (unauthenticated).
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
