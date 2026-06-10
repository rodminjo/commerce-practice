package com.rodminjo.commerce.common.time;

import java.time.Instant;

/**
 * 현재 시각 공통 계약. {@link Instant#now()} 직접 호출 금지. 시각을 주입 의존으로 처리하여 테스트 시 고정 클록으로 교체 가능.
 *
 * <p>{@code common-core}(프레임워크 비의존)에 위치. 시스템 구현체는 {@code common-infra}에 위치. {@code IdGenerator} 분리
 * 패턴과 동일.
 */
@FunctionalInterface
public interface ClockHolder {

  Instant now();
}
