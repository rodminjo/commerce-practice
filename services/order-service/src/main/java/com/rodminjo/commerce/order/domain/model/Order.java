package com.rodminjo.commerce.order.domain.model;

import com.rodminjo.commerce.common.error.DomainException;
import com.rodminjo.commerce.order.domain.OrderErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;

@Getter
public class Order {

  private final UUID id;
  private final String customerId;
  private OrderStatus status;
  private final List<OrderLineItem> items;
  private final Money total;
  private final Instant createdAt;

  private Order(
      UUID id,
      String customerId,
      OrderStatus status,
      List<OrderLineItem> items,
      Money total,
      Instant createdAt) {
    this.id = id;
    this.customerId = customerId;
    this.status = status;
    this.items = List.copyOf(items);
    this.total = total;
    this.createdAt = createdAt;
  }

  public static Order place(
      UUID id, String customerId, List<OrderLineItem> items, String currency, Instant now) {
    if (id == null) {
      throw new DomainException(OrderErrorCode.INVALID_ORDER, "id는 필수입니다");
    }
    if (customerId == null || customerId.isBlank()) {
      throw new DomainException(OrderErrorCode.INVALID_ORDER, "customerId는 비어 있을 수 없습니다");
    }
    if (items == null || items.isEmpty()) {
      throw new DomainException(OrderErrorCode.INVALID_ORDER, "주문 항목은 비어 있을 수 없습니다");
    }

    // 각 라인 합계를 Money로 누적 — reduce가 라인 통화 == 주문 통화를 강제(다르면 예외).
    Money total =
        items.stream().map(OrderLineItem::lineTotal).reduce(Money.zero(currency), Money::plus);

    return new Order(id, customerId, OrderStatus.PENDING, items, total, now);
  }

  public static Order reconstitute(
      UUID id,
      String customerId,
      OrderStatus status,
      List<OrderLineItem> items,
      long totalAmountMinor,
      String currency,
      Instant createdAt) {
    return new Order(
        id, customerId, status, items, Money.of(totalAmountMinor, currency), createdAt);
  }

  public void confirm() {
    if (!status.canTransitionTo(OrderStatus.CONFIRMED)) {
      throw new DomainException(OrderErrorCode.INVALID_STATE_TRANSITION);
    }
    this.status = OrderStatus.CONFIRMED;
  }

  public void cancel() {
    if (!status.canTransitionTo(OrderStatus.CANCELLED)) {
      throw new DomainException(OrderErrorCode.INVALID_STATE_TRANSITION);
    }
    this.status = OrderStatus.CANCELLED;
  }

  /** 환불 확정 전이(CONFIRMED/COMPLETED → REFUNDED). 가드 위반 시 예외. */
  public void refund() {
    if (!status.canTransitionTo(OrderStatus.REFUNDED)) {
      throw new DomainException(OrderErrorCode.INVALID_STATE_TRANSITION);
    }
    this.status = OrderStatus.REFUNDED;
  }
}
