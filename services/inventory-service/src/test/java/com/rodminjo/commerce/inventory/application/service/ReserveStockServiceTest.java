package com.rodminjo.commerce.inventory.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rodminjo.commerce.common.error.DomainException;
import com.rodminjo.commerce.common.time.ClockHolder;
import com.rodminjo.commerce.events.inventory.InventoryReserved;
import com.rodminjo.commerce.inventory.application.port.in.ReserveStockUseCase.ReserveStockCommand;
import com.rodminjo.commerce.inventory.application.port.in.ReserveStockUseCase.ReserveStockCommand.Line;
import com.rodminjo.commerce.inventory.application.port.out.ReservationPort;
import com.rodminjo.commerce.inventory.application.service.support.FakeInventoryStockPort;
import com.rodminjo.commerce.inventory.application.service.support.FakeOutboxAppender;
import com.rodminjo.commerce.inventory.application.service.support.FakeOutboxAppender.Appended;
import com.rodminjo.commerce.inventory.application.service.support.FakeReservationPort;
import com.rodminjo.commerce.inventory.domain.InventoryErrorCode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ReserveStockService")
class ReserveStockServiceTest {

  private static final Instant FIXED_NOW = Instant.parse("2024-01-01T00:00:00Z");
  private final ClockHolder clockHolder = () -> FIXED_NOW;

  private FakeInventoryStockPort stockPort;
  private FakeReservationPort reservationPort;
  private FakeOutboxAppender outboxAppender;
  private ReserveStockService service;

  private static ReserveStockCommand command() {
    return new ReserveStockCommand(
        "order-1", List.of(new Line("prod-1", 2), new Line("prod-2", 1)));
  }

  @BeforeEach
  void setUp() {
    stockPort = new FakeInventoryStockPort();
    reservationPort = new FakeReservationPort();
    outboxAppender = new FakeOutboxAppender();
    service = new ReserveStockService(stockPort, reservationPort, outboxAppender, clockHolder);
  }

  @Nested
  @DisplayName("예약 성공")
  class 예약성공 {

    @Test
    @DisplayName("전 품목 예약 성공 → 예약 저장 + inventory.reserved 적재")
    void reserveHappyPath() {
      stockPort.seed("prod-1", 10, 0);
      stockPort.seed("prod-2", 10, 0);

      service.reserve(command());

      // reservations saved
      assertThat(reservationPort.findActive("order-1"))
          .containsExactly(
              new ReservationPort.ReservedLine("prod-1", 2),
              new ReservationPort.ReservedLine("prod-2", 1));
      // stock actually reserved
      assertThat(stockPort.reserved("prod-1")).isEqualTo(2);
      assertThat(stockPort.reserved("prod-2")).isEqualTo(1);

      assertThat(outboxAppender.appended()).hasSize(1);
      Appended appended = outboxAppender.appended().get(0);
      assertThat(appended.aggregateType()).isEqualTo("Inventory");
      assertThat(appended.aggregateId()).isEqualTo("order-1");
      assertThat(appended.topic()).isEqualTo("inventory.reserved");
      assertThat(appended.partitionKey()).isEqualTo("order-1");
      assertThat(appended.event()).isInstanceOf(InventoryReserved.class);
      InventoryReserved event = (InventoryReserved) appended.event();
      assertThat(event.getOrderId()).isEqualTo("order-1");
      assertThat(event.getItemsList()).hasSize(2);
      assertThat(event.getItems(0).getProductId()).isEqualTo("prod-1");
      assertThat(event.getItems(0).getQuantity()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("예약 실패")
  class 예약실패 {

    @Test
    @DisplayName("한 품목이라도 영향 행 0 → INSUFFICIENT_STOCK, 미적재")
    void insufficientStockThrowsAndDoesNotPublish() {
      stockPort.seed("prod-1", 10, 0);
      stockPort.seed("prod-2", 0, 0); // out of stock

      assertThatThrownBy(() -> service.reserve(command()))
          .isInstanceOf(DomainException.class)
          .extracting(e -> ((DomainException) e).errorCode())
          .isEqualTo(InventoryErrorCode.INSUFFICIENT_STOCK);

      assertThat(reservationPort.saved("order-1")).isEmpty();
      assertThat(outboxAppender.appended()).isEmpty();
    }

    @Test
    @DisplayName("상품이 없으면 PRODUCT_NOT_FOUND")
    void productNotFound() {
      // prod-1 not seeded → exists() returns false

      assertThatThrownBy(() -> service.reserve(command()))
          .isInstanceOf(DomainException.class)
          .extracting(e -> ((DomainException) e).errorCode())
          .isEqualTo(InventoryErrorCode.PRODUCT_NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("멱등성")
  class 멱등성 {

    @Test
    @DisplayName("이미 예약된 주문이면 멱등 스킵 (재예약 X)")
    void idempotentSkipWhenAlreadyReserved() {
      reservationPort.seedActive("order-1", new ReservationPort.ReservedLine("prod-1", 2));
      stockPort.seed("prod-1", 10, 2);
      stockPort.seed("prod-2", 10, 0);

      service.reserve(command());

      // no re-reserve: reserved amounts unchanged from seed
      assertThat(stockPort.reserved("prod-1")).isEqualTo(2);
      assertThat(stockPort.reserved("prod-2")).isEqualTo(0);
      assertThat(outboxAppender.appended()).isEmpty();
    }
  }
}
