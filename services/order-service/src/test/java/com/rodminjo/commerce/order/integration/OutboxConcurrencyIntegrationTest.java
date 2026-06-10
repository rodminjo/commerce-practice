package com.rodminjo.commerce.order.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.rodminjo.commerce.common.outbox.entity.OutboxEvent;
import com.rodminjo.commerce.common.outbox.repository.OutboxRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves the multi-instance relay guarantee from {@code 01-transactional-outbox.md} §3.3: the
 * {@code FOR UPDATE SKIP LOCKED} query lets two relays poll the outbox concurrently and each grabs
 * a <em>disjoint</em> set of PENDING rows — never the same row twice (no duplicate publish).
 *
 * <p>This is the race condition the production query exists to prevent, and the only test that
 * genuinely exercises it: {@link OutboxRelayIntegrationTest} runs {@code publishBatch()} single
 * threaded, so {@code SKIP LOCKED} never contends there and dropping the clause would not fail it.
 * Here two transactions overlap on purpose: relay A locks the oldest batch and holds its
 * transaction open while relay B polls. With {@code SKIP LOCKED} present, B skips A's locked rows
 * and proceeds immediately with the next batch (assertion below). Without it, B would block on A's
 * row locks until A commits — A only commits after B finishes, so the two deadlock and the test
 * times out. Either way, removing {@code SKIP LOCKED} fails this test.
 *
 * <p>Lives in its own class (own context + embedded Postgres + Kafka broker) so the seeded PENDING
 * rows it leaves behind do not pollute the exact outbox/topic counts that {@link
 * OutboxRelayIntegrationTest} asserts. Docker-free: Zonky embedded Postgres +
 * {@code @EmbeddedKafka}.
 */
@SpringBootTest
@EmbeddedKafka(
    topics = {"order.placed", "order.cancelled"},
    partitions = 1,
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class OutboxConcurrencyIntegrationTest {

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
    // Disable scheduled polling — we drive the locking query manually in two threads.
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

  @Autowired private OutboxRepository outboxRepository;

  @Autowired private OutboxTestRepository outboxTestRepository;

  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  @DisplayName(
      "FOR UPDATE SKIP LOCKED: two concurrent relays lock disjoint PENDING batches (no double publish)")
  void skipLocked_concurrentRelays_grabDisjointRows() throws Exception {
    // Seed 4 PENDING rows with strictly increasing created_at so ORDER BY created_at is
    // deterministic (oldest first). limit=2 means each relay claims a 2-row batch.
    Instant base = Instant.parse("2024-01-01T00:00:00Z");
    List<UUID> seededIds = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      UUID id = UUID.randomUUID();
      seededIds.add(id);
      outboxTestRepository.save(
          OutboxEvent.pending(
              id,
              "Order",
              "concurrency-" + i,
              "commerce.events.order.OrderPlaced",
              "order.placed",
              "concurrency-" + i,
              new byte[] {1},
              base.plusSeconds(i)));
    }

    // Relay A locks first and HOLDS its transaction open until B has run its own locking query,
    // guaranteeing the two queries genuinely overlap (A's lock is live while B polls).
    CountDownLatch aHasLocked = new CountDownLatch(1);
    CountDownLatch bHasLocked = new CountDownLatch(1);

    TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<List<UUID>> relayA =
          pool.submit(
              () ->
                  txTemplate.execute(
                      status -> {
                        List<UUID> ids = lockedIds(outboxRepository.lockPendingBatch(2));
                        aHasLocked.countDown();
                        // Hold the transaction (and the row locks) open while B polls.
                        awaitOrFail(bHasLocked, "B never finished its locking query");
                        return ids;
                      }));

      Future<List<UUID>> relayB =
          pool.submit(
              () -> {
                awaitOrFail(aHasLocked, "A never acquired its lock");
                return txTemplate.execute(
                    status -> {
                      // SKIP LOCKED → returns the rows A did NOT lock, without blocking.
                      List<UUID> ids = lockedIds(outboxRepository.lockPendingBatch(2));
                      bHasLocked.countDown();
                      return ids;
                    });
              });

      List<UUID> aIds = relayA.get(20, TimeUnit.SECONDS);
      List<UUID> bIds = relayB.get(20, TimeUnit.SECONDS);

      // Each relay claimed its own 2-row batch...
      assertThat(aIds).hasSize(2);
      assertThat(bIds).hasSize(2);
      // ...and the batches are disjoint — no row was handed to both relays (the core guarantee).
      assertThat(aIds).doesNotContainAnyElementsOf(bIds);
      // Together they cover all 4 seeded PENDING rows.
      assertThat(Stream.concat(aIds.stream(), bIds.stream()).toList())
          .containsExactlyInAnyOrderElementsOf(seededIds);
    } finally {
      pool.shutdownNow();
    }
  }

  private static List<UUID> lockedIds(List<OutboxEvent> events) {
    return events.stream().map(OutboxEvent::getId).toList();
  }

  private static void awaitOrFail(CountDownLatch latch, String message) {
    try {
      if (!latch.await(15, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out: " + message);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted: " + message, e);
    }
  }
}
