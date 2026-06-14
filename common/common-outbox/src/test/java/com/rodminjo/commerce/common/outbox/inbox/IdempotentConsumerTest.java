package com.rodminjo.commerce.common.outbox.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rodminjo.commerce.common.outbox.OutboxAutoConfiguration;
import com.rodminjo.commerce.common.time.ClockHolder;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 멱등 컨슈머 슬라이스 테스트. UNIQUE 충돌 감지(saveAndFlush)와 핸들러 예외 시 dedup 행 롤백은 실DB로만 검증 가능하므로 Zonky 임베디드
 * Postgres를 사용. {@code processed_event} 테이블은 common이 DDL을 소유하지 않으므로 테스트에서 Hibernate {@code
 * ddl-auto}로 생성.
 *
 * <p>{@code once()}가 {@code @Transactional}이고 테스트 메서드 자체는 비트랜잭션이므로, 각 호출은 독립적으로 커밋/롤백된다 — 핸들러 예외
 * 케이스에서 dedup 행 롤백을 그대로 관측할 수 있다.
 */
@SpringBootTest
class IdempotentConsumerTest {

  private static EmbeddedPostgres embeddedPg;

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) throws IOException {
    embeddedPg = EmbeddedPostgres.builder().start();
    registry.add("spring.datasource.url", () -> embeddedPg.getJdbcUrl("postgres", "postgres"));
    registry.add("spring.datasource.username", () -> "postgres");
    registry.add("spring.datasource.password", () -> "postgres");
    // common은 마이그레이션 DDL을 소유하지 않음 — 슬라이스 테스트 한정으로 스키마 자동 생성.
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
  }

  @AfterAll
  static void stopEmbeddedPg() throws IOException {
    if (embeddedPg != null) {
      embeddedPg.close();
    }
  }

  @Autowired private IdempotentConsumer idempotentConsumer;

  @Autowired private ProcessedEventRepository processedEventRepository;

  private static final String GROUP = "test-service";

  @Nested
  @DisplayName("once 호출 시")
  class Once {

    @Test
    @DisplayName("신규 메시지 → handler 실행 후 true 반환, dedup 행 적재")
    void newMessage_runsHandler_andReturnsTrue() {
      UUID eventId = UUID.randomUUID();
      AtomicInteger ran = new AtomicInteger();

      boolean result = idempotentConsumer.once(GROUP, eventId, ran::incrementAndGet);

      assertThat(result).isTrue();
      assertThat(ran.get()).isEqualTo(1);
      assertThat(processedEventRepository.existsById(new ProcessedEventId(GROUP, eventId)))
          .isTrue();
    }

    @Test
    @DisplayName("이미 처리된 메시지 → handler 미실행 후 false 반환")
    void duplicateMessage_doesNotRunHandler_andReturnsFalse() {
      UUID eventId = UUID.randomUUID();
      idempotentConsumer.once(GROUP, eventId, () -> {});

      AtomicInteger ran = new AtomicInteger();
      boolean result = idempotentConsumer.once(GROUP, eventId, ran::incrementAndGet);

      assertThat(result).isFalse();
      assertThat(ran.get()).isZero();
    }

    @Test
    @DisplayName("handler 예외 → dedup 행까지 롤백 → 재전달 시 재처리 가능")
    void handlerThrows_rollsBackDedupRow_soReprocessable() {
      UUID eventId = UUID.randomUUID();

      // 첫 처리에서 핸들러가 예외 → 전체 트랜잭션(dedup 행 포함) 롤백.
      assertThatThrownBy(
              () ->
                  idempotentConsumer.once(
                      GROUP,
                      eventId,
                      () -> {
                        throw new IllegalStateException("handler boom");
                      }))
          .isInstanceOf(IllegalStateException.class);

      // dedup 행이 남지 않아야 함.
      assertThat(processedEventRepository.existsById(new ProcessedEventId(GROUP, eventId)))
          .isFalse();

      // 재전달: 같은 eventId가 신규로 취급되어 핸들러가 다시 실행되고 true 반환.
      AtomicInteger ran = new AtomicInteger();
      boolean result = idempotentConsumer.once(GROUP, eventId, ran::incrementAndGet);

      assertThat(result).isTrue();
      assertThat(ran.get()).isEqualTo(1);
      assertThat(processedEventRepository.existsById(new ProcessedEventId(GROUP, eventId)))
          .isTrue();
    }
  }

  @Nested
  @DisplayName("동시성")
  class Concurrency {

    @Test
    @DisplayName("같은 eventId N개 동시 호출 → handler 정확히 1회, true 1개, dedup 1행")
    void concurrentSameEvent_runsHandlerExactlyOnce() throws InterruptedException {
      UUID eventId = UUID.randomUUID();
      int threads = 8;
      AtomicInteger handlerRuns = new AtomicInteger();
      AtomicInteger trueCount = new AtomicInteger();
      ExecutorService pool = Executors.newFixedThreadPool(threads);
      CountDownLatch start = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(threads);

      for (int i = 0; i < threads; i++) {
        pool.submit(
            () -> {
              try {
                start.await();
                if (idempotentConsumer.once(GROUP, eventId, handlerRuns::incrementAndGet)) {
                  trueCount.incrementAndGet();
                }
              } catch (Exception ignored) {
                // 경합 패자는 설계상 DataIntegrityViolationException 전파(재전달 유도) — 무시.
              } finally {
                done.countDown();
              }
            });
      }
      start.countDown(); // 동시에 출발
      assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
      pool.shutdownNow();

      // 동시 도착해도 부수효과는 정확히 1회, 성공(true)도 1개, dedup 행 1개.
      assertThat(handlerRuns.get()).isEqualTo(1);
      assertThat(trueCount.get()).isEqualTo(1);
      assertThat(processedEventRepository.existsById(new ProcessedEventId(GROUP, eventId)))
          .isTrue();
    }
  }

  // OutboxAutoConfiguration은 제외 — IdempotentConsumer/리포지토리/엔티티를 테스트에서 직접 구성하여
  // KafkaTemplate 등 발행 측 협력 빈 없이 inbox 슬라이스만 띄운다.
  @SpringBootConfiguration
  @EnableAutoConfiguration(exclude = OutboxAutoConfiguration.class)
  @EnableJpaRepositories(basePackageClasses = ProcessedEventRepository.class)
  @EntityScan(basePackageClasses = ProcessedEvent.class)
  static class TestConfig {

    @Bean
    ClockHolder clockHolder() {
      return () -> Instant.parse("2024-01-01T00:00:00Z");
    }

    @Bean
    IdempotentConsumer idempotentConsumer(
        ProcessedEventRepository repository, ClockHolder clockHolder) {
      return new IdempotentConsumer(repository, clockHolder);
    }
  }
}
