package com.rodminjo.commerce.order.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 전체 주문 상태 머신 잠금 테스트. 모든 (from, to) 쌍을 명세로 열거 — 프로덕션 전이 맵과 독립적으로 작성하여 어느 쪽을 실수로 수정해도 즉시 탐지. */
class OrderStatusTest {

  @ParameterizedTest(name = "{0} -> {1} == {2}")
  @DisplayName("canTransitionTo(): 전체 상태 전이 매트릭스")
  @CsvSource({
    // PENDING 출발
    "PENDING,   PENDING,   false",
    "PENDING,   CONFIRMED, true",
    "PENDING,   COMPLETED, false",
    "PENDING,   CANCELLED, true",
    "PENDING,   REFUNDED,  false",
    // CONFIRMED 출발
    "CONFIRMED, PENDING,   false",
    "CONFIRMED, CONFIRMED, false",
    "CONFIRMED, COMPLETED, true",
    "CONFIRMED, CANCELLED, true",
    "CONFIRMED, REFUNDED,  true",
    // COMPLETED 출발
    "COMPLETED, PENDING,   false",
    "COMPLETED, CONFIRMED, false",
    "COMPLETED, COMPLETED, false",
    "COMPLETED, CANCELLED, false",
    "COMPLETED, REFUNDED,  true",
    // CANCELLED 출발 (종료 상태)
    "CANCELLED, PENDING,   false",
    "CANCELLED, CONFIRMED, false",
    "CANCELLED, COMPLETED, false",
    "CANCELLED, CANCELLED, false",
    "CANCELLED, REFUNDED,  false",
    // REFUNDED 출발 (종료 상태)
    "REFUNDED,  PENDING,   false",
    "REFUNDED,  CONFIRMED, false",
    "REFUNDED,  COMPLETED, false",
    "REFUNDED,  CANCELLED, false",
    "REFUNDED,  REFUNDED,  false",
  })
  void transitionMatrix(OrderStatus from, OrderStatus to, boolean expected) {
    assertThat(from.canTransitionTo(to)).as("%s -> %s", from, to).isEqualTo(expected);
  }
}
