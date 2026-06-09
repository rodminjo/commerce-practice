package com.rodminjo.commerce.order.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Locks the full order state machine. Every (from, to) pair is listed explicitly as the spec —
 * independent of the production transition map — so an accidental edit to either side is caught.
 */
class OrderStatusTest {

  @ParameterizedTest(name = "{0} -> {1} == {2}")
  @DisplayName("canTransitionTo(): 전체 상태 전이 매트릭스")
  @CsvSource({
    // from PENDING
    "PENDING,   PENDING,   false",
    "PENDING,   CONFIRMED, true",
    "PENDING,   COMPLETED, false",
    "PENDING,   CANCELLED, true",
    "PENDING,   REFUNDED,  false",
    // from CONFIRMED
    "CONFIRMED, PENDING,   false",
    "CONFIRMED, CONFIRMED, false",
    "CONFIRMED, COMPLETED, true",
    "CONFIRMED, CANCELLED, true",
    "CONFIRMED, REFUNDED,  false",
    // from COMPLETED
    "COMPLETED, PENDING,   false",
    "COMPLETED, CONFIRMED, false",
    "COMPLETED, COMPLETED, false",
    "COMPLETED, CANCELLED, false",
    "COMPLETED, REFUNDED,  true",
    // from CANCELLED (terminal)
    "CANCELLED, PENDING,   false",
    "CANCELLED, CONFIRMED, false",
    "CANCELLED, COMPLETED, false",
    "CANCELLED, CANCELLED, false",
    "CANCELLED, REFUNDED,  false",
    // from REFUNDED (terminal)
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
