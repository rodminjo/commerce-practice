package com.rodminjo.commerce.common.outbox.relay;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 아웃박스 릴레이 스케줄링 트리거. {@link OutboxRelay}와 의도적으로 별도 빈으로 분리. {@link OutboxRelay#publishBatch()} 호출이 빈
 * 경계를 넘어 Spring 프록시를 거치므로 {@code @Transactional} 어드바이스가 정상 적용됨. (같은 빈 내 {@code @Scheduled} →
 * {@code @Transactional} 자기 호출 시 트랜잭션 묵시적 누락 방지.)
 *
 * <p>소비 애플리케이션에 {@code @EnableScheduling} 필요. 폴 간격은 {@code outbox.relay.poll-interval-ms}({@link
 * OutboxRelayProperties} 참조).
 */
@RequiredArgsConstructor
public class OutboxRelayScheduler {

  private final OutboxRelay relay;

  @Scheduled(fixedDelayString = "${outbox.relay.poll-interval-ms:1000}")
  public void poll() {
    relay.publishBatch();
  }
}
