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
 * 멀티 인스턴스 릴레이 보장 검증({@code 01-transactional-outbox.md} §3.3): {@code FOR UPDATE SKIP LOCKED} 쿼리로 두
 * 릴레이가 동시에 outbox를 폴링할 때 각각 <em>서로 다른</em> PENDING 행 집합을 획득 — 동일 행 중복 발행 없음.
 *
 * <p>프로덕션 쿼리가 방지하는 경쟁 조건이며 이를 실제로 검증하는 유일한 테스트: {@link OutboxRelayIntegrationTest}는 {@code
 * publishBatch()}를 단일 스레드로 실행하므로 {@code SKIP LOCKED}가 경합하지 않아 해당 절을 제거해도 실패하지 않음. 여기서는 두 트랜잭션을
 * 의도적으로 겹침: 릴레이 A가 최오래된 배치를 잠그고 트랜잭션을 열어둔 채 B가 폴링. {@code SKIP LOCKED} 존재 시 B는 A의 잠긴 행을 건너뛰고 즉시 다음
 * 배치를 처리(아래 단언). 없으면 B가 A의 행 잠금 해제 때까지 블록 — A는 B 완료 후에야 커밋하므로 데드락 및 타임아웃 발생. 어느 쪽이든 {@code SKIP
 * LOCKED} 제거 시 이 테스트 실패.
 *
 * <p>독립 클래스(자체 컨텍스트 + 임베디드 Postgres + Kafka)로 분리하여 시드된 PENDING 행이 {@link
 * OutboxRelayIntegrationTest}의 정확한 outbox/토픽 카운트 단언을 오염시키지 않도록 방지. Docker 불필요: Zonky 임베디드 Postgres
 * + {@code @EmbeddedKafka}.
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
    // 스케줄 폴링 비활성화 — 잠금 쿼리를 두 스레드에서 수동 구동.
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
  @DisplayName("FOR UPDATE SKIP LOCKED: 동시 릴레이 2개가 서로 다른 PENDING 배치를 잠금 (중복 발행 없음)")
  void skipLocked_concurrentRelays_grabDisjointRows() throws Exception {
    // created_at 단조 증가로 PENDING 행 4개 시드 — ORDER BY created_at 결정론적(가장 오래된 순).
    // limit=2이므로 각 릴레이가 2행 배치를 획득.
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

    // 릴레이 A가 먼저 잠금 후 B의 잠금 쿼리 실행 완료까지 트랜잭션 유지 —
    // 두 쿼리가 진정으로 겹치도록 보장(A의 잠금이 B 폴링 중 유효).
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
                        // B 폴링 중 트랜잭션(및 행 잠금) 유지.
                        awaitOrFail(bHasLocked, "B never finished its locking query");
                        return ids;
                      }));

      Future<List<UUID>> relayB =
          pool.submit(
              () -> {
                awaitOrFail(aHasLocked, "A never acquired its lock");
                return txTemplate.execute(
                    status -> {
                      // SKIP LOCKED → A가 잠그지 않은 행을 블로킹 없이 반환.
                      List<UUID> ids = lockedIds(outboxRepository.lockPendingBatch(2));
                      bHasLocked.countDown();
                      return ids;
                    });
              });

      List<UUID> aIds = relayA.get(20, TimeUnit.SECONDS);
      List<UUID> bIds = relayB.get(20, TimeUnit.SECONDS);

      // 각 릴레이가 자신의 2행 배치를 획득...
      assertThat(aIds).hasSize(2);
      assertThat(bIds).hasSize(2);
      // ...배치는 서로 다름 — 동일 행이 두 릴레이 모두에 전달되지 않음(핵심 보장).
      assertThat(aIds).doesNotContainAnyElementsOf(bIds);
      // 합산하면 시드된 PENDING 행 4개를 모두 포함.
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
