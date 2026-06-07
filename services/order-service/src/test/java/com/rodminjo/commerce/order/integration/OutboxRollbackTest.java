package com.rodminjo.commerce.order.integration;

import com.rodminjo.commerce.common.outbox.OutboxAppender;
import com.rodminjo.commerce.common.outbox.OutboxRepository;
import com.rodminjo.commerce.order.adapter.out.persistence.jpa.OrderJpaRepository;
import com.rodminjo.commerce.order.application.port.in.PlaceOrderUseCase;
import com.rodminjo.commerce.order.application.port.in.PlaceOrderUseCase.PlaceOrderCommand;
import com.rodminjo.commerce.order.application.port.in.PlaceOrderUseCase.PlaceOrderCommand.OrderItemCommand;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

/**
 * Proves the dual-write atomicity guarantee: when the outbox append fails, the order INSERT must
 * roll back too (both writes share one transaction). Docker-free: real embedded Postgres (Zonky).
 * Verification goes through JPA repositories, not raw SQL.
 */
@SpringBootTest
@EmbeddedKafka(topics = {"order.placed", "order.cancelled"}, partitions = 1,
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
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "http://localhost:9999/realms/test");
    }

    @AfterAll
    static void stopEmbeddedPg() throws IOException {
        if (embeddedPg != null) {
            embeddedPg.close();
        }
    }

    @MockitoBean
    private OutboxAppender outboxAppender;

    @Autowired
    private PlaceOrderUseCase placeOrderUseCase;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Test
    @DisplayName("B: OutboxAppender throws → transaction rollback → no order and no outbox row")
    void rollback_onOutboxFailure() {
        doThrow(new RuntimeException("simulated outbox failure"))
                .when(outboxAppender).append(anyString(), anyString(), anyString(), anyString(), any());

        PlaceOrderCommand cmd = new PlaceOrderCommand(
                "customer-rollback-1",
                List.of(new OrderItemCommand("prod-rollback", 1, 1000L)),
                "USD"
        );

        assertThatThrownBy(() -> placeOrderUseCase.place(cmd))
                .isInstanceOf(RuntimeException.class);

        // Transaction rolled back — neither the order nor an outbox row was persisted.
        assertThat(orderJpaRepository.countByCustomerId("customer-rollback-1")).isZero();
        assertThat(outboxRepository.countByAggregateType("Order")).isZero();
    }
}
