package com.rodminjo.commerce.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rodminjo.commerce.common.error.DomainException;
import com.rodminjo.commerce.inventory.domain.model.Inventory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Locks the oversell invariant the atomic SQL UPDATE enforces in production. */
class InventoryDomainTest {

  @Nested
  @DisplayName("reserve()")
  class Reserve {

    @Test
    @DisplayName("가용 수량 내 예약은 reserved를 늘리고 available을 줄인다")
    void reserveWithinAvailable() {
      Inventory inventory = Inventory.of("prod-1", 100, 10);

      Inventory reserved = inventory.reserve(30);

      assertThat(reserved.getReserved()).isEqualTo(40);
      assertThat(reserved.getStock()).isEqualTo(100);
      assertThat(reserved.availableQty()).isEqualTo(60);
    }

    @Test
    @DisplayName("가용 수량을 초과하면 INSUFFICIENT_STOCK")
    void reserveBeyondAvailableThrows() {
      Inventory inventory = Inventory.of("prod-2", 5, 0);

      assertThatThrownBy(() -> inventory.reserve(6))
          .isInstanceOf(DomainException.class)
          .extracting(e -> ((DomainException) e).errorCode())
          .isEqualTo(InventoryErrorCode.INSUFFICIENT_STOCK);
    }

    @Test
    @DisplayName("정확히 가용 수량만큼 예약은 허용된다 (경계값)")
    void reserveExactlyAvailable() {
      Inventory inventory = Inventory.of("prod-2", 5, 0);

      assertThat(inventory.reserve(5).availableQty()).isZero();
    }

    @Test
    @DisplayName("0 이하 수량은 INVALID_INVENTORY")
    void reserveNonPositiveThrows() {
      Inventory inventory = Inventory.of("prod-1", 100, 0);

      assertThatThrownBy(() -> inventory.reserve(0))
          .isInstanceOf(DomainException.class)
          .extracting(e -> ((DomainException) e).errorCode())
          .isEqualTo(InventoryErrorCode.INVALID_INVENTORY);
    }
  }

  @Nested
  @DisplayName("release()")
  class Release {

    @Test
    @DisplayName("예약 수량 내 복구는 reserved를 줄인다")
    void releaseWithinReserved() {
      Inventory inventory = Inventory.of("prod-1", 100, 40);

      assertThat(inventory.release(30).getReserved()).isEqualTo(10);
    }

    @Test
    @DisplayName("예약 수량보다 많이 복구하면 INVALID_INVENTORY")
    void releaseBeyondReservedThrows() {
      Inventory inventory = Inventory.of("prod-1", 100, 10);

      assertThatThrownBy(() -> inventory.release(11))
          .isInstanceOf(DomainException.class)
          .extracting(e -> ((DomainException) e).errorCode())
          .isEqualTo(InventoryErrorCode.INVALID_INVENTORY);
    }
  }

  @Test
  @DisplayName("of(): reserved가 stock을 넘으면 생성 거부")
  void invalidConstruction() {
    assertThatThrownBy(() -> Inventory.of("prod-1", 5, 6)).isInstanceOf(DomainException.class);
  }
}
