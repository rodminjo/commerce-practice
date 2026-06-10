package com.rodminjo.commerce.common.outbox.relay;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 아웃박스 릴레이 설정. {@code outbox.relay.*}에서 바인딩.
 *
 * <p>분산 {@code @Value} 조회를 단일 타입 객체로 대체. 기본값은 여기서 적용(setter에서도 재확인)하여 누락 또는 비양수 값으로 인한 비정상 폴/배치 크기
 * 방지.
 */
@Getter
@ConfigurationProperties(prefix = "outbox.relay")
public class OutboxRelayProperties {

  /** 스케줄러 폴 간격(밀리초). {@code @Scheduled} 플레이스홀더에서도 참조. */
  private long pollIntervalMs = 1000L;

  /** 폴당 조회·발행하는 PENDING 이벤트 최대 개수. */
  private int batchSize = 100;

  public void setPollIntervalMs(long pollIntervalMs) {
    this.pollIntervalMs = pollIntervalMs > 0 ? pollIntervalMs : 1000L;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize > 0 ? batchSize : 100;
  }
}
