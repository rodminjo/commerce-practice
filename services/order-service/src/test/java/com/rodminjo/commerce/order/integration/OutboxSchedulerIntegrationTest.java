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
 * Regression guard for the outbox relay self-invocation bug. Previously {@code OutboxRelay.poll()}
 * called {@code this.publishBatch()} on the same bean, so the call bypassed the Spring proxy, no
 * transaction ran, {@code markPublished()} never flushed, and the row stayed PENDING — re-published
 * on every poll. The scheduling trigger now lives in {@link OutboxRelayScheduler} (a separate
 * bean), so driving {@code poll()} crosses the proxy and the {@code @Transactional} advice applies.
 *
 * <p>Lives in its own class (own context + embedded Postgres + Kafka broker) so its extra publish
 * does not pollute the shared {@code order.placed} topic that {@code OutboxRelayIntegrationTest}
 * asserts exact record counts on. Docker-free: Zonky embedded Postgres + {@code @EmbeddedKafka}.
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
    // Disable the real scheduled cadence — we drive poll() manually.
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
  @DisplayName("scheduler.poll() crosses the proxy → @Transactional applies, row marked PUBLISHED")
  void scheduledPoll_appliesTransaction_andMarksPublished() {
    PlaceOrderCommand cmd =
        new PlaceOrderCommand(
            "customer-scheduler-1", List.of(new OrderItemCommand("prod-1", 1, 700L)), "KRW");
    UUID orderId = placeOrderUseCase.place(cmd).orderId();

    // sanity: appended PENDING in place()'s own transaction
    assertThat(outboxRepository.findByAggregateId(orderId.toString()))
        .singleElement()
        .satisfies(e -> assertThat(e.getStatus()).isEqualTo(OutboxStatus.PENDING));

    // production path: scheduler bean → relay bean → proxy → @Transactional publishBatch()
    outboxRelayScheduler.poll();

    // with the old self-invocation the row would still be PENDING here
    OutboxEvent afterPoll = outboxRepository.findByAggregateId(orderId.toString()).get(0);
    assertThat(afterPoll.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    assertThat(afterPoll.getPublishedAt()).isNotNull();
  }
}
