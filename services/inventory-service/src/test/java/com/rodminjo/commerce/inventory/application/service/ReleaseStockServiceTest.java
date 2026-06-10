package com.rodminjo.commerce.inventory.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.rodminjo.commerce.common.time.ClockHolder;
import com.rodminjo.commerce.events.inventory.InventoryReleased;
import com.rodminjo.commerce.inventory.application.port.in.ReleaseStockUseCase.ReleaseStockCommand;
import com.rodminjo.commerce.inventory.application.port.out.ReservationPort.ReservedLine;
import com.rodminjo.commerce.inventory.application.service.support.FakeInventoryStockPort;
import com.rodminjo.commerce.inventory.application.service.support.FakeOutboxAppender;
import com.rodminjo.commerce.inventory.application.service.support.FakeOutboxAppender.Appended;
import com.rodminjo.commerce.inventory.application.service.support.FakeReservationPort;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReleaseStockServiceTest {

  private static final Instant FIXED_NOW = Instant.parse("2024-01-01T00:00:00Z");
  private final ClockHolder clockHolder = () -> FIXED_NOW;

  private FakeInventoryStockPort stockPort;
  private FakeReservationPort reservationPort;
  private FakeOutboxAppender outboxAppender;
  private ReleaseStockService service;

  @BeforeEach
  void setUp() {
    stockPort = new FakeInventoryStockPort();
    reservationPort = new FakeReservationPort();
    outboxAppender = new FakeOutboxAppender();
    service = new ReleaseStockService(stockPort, reservationPort, outboxAppender, clockHolder);
  }

  @Test
  @DisplayName("활성 예약을 복구하고 RELEASED 처리 + inventory.released 적재")
  void releaseActiveReservations() {
    stockPort.seed("prod-1", 10, 2);
    stockPort.seed("prod-2", 10, 1);
    reservationPort.seedActive(
        "order-1", new ReservedLine("prod-1", 2), new ReservedLine("prod-2", 1));

    service.release(new ReleaseStockCommand("order-1", "user-cancel"));

    // stock restored
    assertThat(stockPort.reserved("prod-1")).isEqualTo(0);
    assertThat(stockPort.reserved("prod-2")).isEqualTo(0);
    // reservations marked RELEASED → no longer active
    assertThat(reservationPort.findActive("order-1")).isEmpty();

    assertThat(outboxAppender.appended()).hasSize(1);
    Appended appended = outboxAppender.appended().get(0);
    assertThat(appended.aggregateType()).isEqualTo("Inventory");
    assertThat(appended.aggregateId()).isEqualTo("order-1");
    assertThat(appended.topic()).isEqualTo("inventory.released");
    assertThat(appended.partitionKey()).isEqualTo("order-1");
    assertThat(appended.event()).isInstanceOf(InventoryReleased.class);
    assertThat(((InventoryReleased) appended.event()).getReason()).isEqualTo("user-cancel");
  }

  @Test
  @DisplayName("활성 예약이 없으면 멱등 스킵 (복구 X, 미적재)")
  void idempotentSkipWhenNothingReserved() {
    stockPort.seed("prod-1", 10, 0);

    service.release(new ReleaseStockCommand("order-1", "user-cancel"));

    // nothing released, stock untouched
    assertThat(stockPort.reserved("prod-1")).isEqualTo(0);
    assertThat(reservationPort.findActive("order-1")).isEmpty();
    assertThat(outboxAppender.appended()).isEmpty();
  }
}
