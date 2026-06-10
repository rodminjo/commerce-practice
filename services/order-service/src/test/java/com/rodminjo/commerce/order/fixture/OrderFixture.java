package com.rodminjo.commerce.order.fixture;

import com.rodminjo.commerce.order.application.port.in.PlaceOrderUseCase.PlaceOrderCommand;
import com.rodminjo.commerce.order.application.port.in.PlaceOrderUseCase.PlaceOrderCommand.OrderItemCommand;
import com.rodminjo.commerce.order.domain.model.Order;
import com.rodminjo.commerce.order.domain.model.OrderLineItem;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Order 애그리거트 및 {@link PlaceOrderCommand} 테스트 데이터 빌더.
 *
 * <p>기본값은 유효한 PENDING 주문을 생성하며, 테스트는 관심 있는 필드만 오버라이드 (예: {@code
 * OrderFixture.anOrder().currency("USD").build()}).
 *
 * <p>유효하지 않은 입력(null/blank/잘못된 currency) 케이스는 여기를 통하지 말고 테스트 호출부에서 직접 전달 — 문제 값이 코드상 명확히 드러나도록 유지.
 */
public final class OrderFixture {

  public static final Instant FIXED_NOW = Instant.parse("2024-01-01T00:00:00Z");
  public static final String DEFAULT_CUSTOMER_ID = "customer-1";
  public static final String DEFAULT_PRODUCT_ID = "product-A";
  public static final String DEFAULT_CURRENCY = "KRW";

  private OrderFixture() {}

  /** 기본 라인 아이템: {@value #DEFAULT_PRODUCT_ID} 1개 @ 1000. */
  public static OrderLineItem lineItem() {
    return OrderLineItem.of(DEFAULT_PRODUCT_ID, 1, 1000L);
  }

  /** 기본 필드로 구성된 유효한 PENDING {@link Order}. */
  public static Order order() {
    return anOrder().build();
  }

  public static OrderBuilder anOrder() {
    return new OrderBuilder();
  }

  /** 기본 필드로 구성된 유효한 {@link PlaceOrderCommand}. */
  public static PlaceOrderCommand placeOrderCommand() {
    return aPlaceOrderCommand().build();
  }

  public static PlaceOrderCommandBuilder aPlaceOrderCommand() {
    return new PlaceOrderCommandBuilder();
  }

  public static final class OrderBuilder {
    private UUID id = UUID.randomUUID();
    private String customerId = DEFAULT_CUSTOMER_ID;
    private List<OrderLineItem> items = List.of(lineItem());
    private String currency = DEFAULT_CURRENCY;
    private Instant now = FIXED_NOW;

    public OrderBuilder id(UUID id) {
      this.id = id;
      return this;
    }

    public OrderBuilder customerId(String customerId) {
      this.customerId = customerId;
      return this;
    }

    public OrderBuilder items(List<OrderLineItem> items) {
      this.items = items;
      return this;
    }

    public OrderBuilder items(OrderLineItem... items) {
      this.items = List.of(items);
      return this;
    }

    public OrderBuilder currency(String currency) {
      this.currency = currency;
      return this;
    }

    public OrderBuilder now(Instant now) {
      this.now = now;
      return this;
    }

    public Order build() {
      return Order.place(id, customerId, items, currency, now);
    }
  }

  public static final class PlaceOrderCommandBuilder {
    private String customerId = DEFAULT_CUSTOMER_ID;
    private List<OrderItemCommand> items =
        List.of(new OrderItemCommand(DEFAULT_PRODUCT_ID, 1, 1000L));
    private String currency = DEFAULT_CURRENCY;

    public PlaceOrderCommandBuilder customerId(String customerId) {
      this.customerId = customerId;
      return this;
    }

    public PlaceOrderCommandBuilder items(List<OrderItemCommand> items) {
      this.items = items;
      return this;
    }

    /** 라인 아이템을 단일 아이템으로 교체. */
    public PlaceOrderCommandBuilder item(String productId, int quantity, long unitPriceMinor) {
      this.items = List.of(new OrderItemCommand(productId, quantity, unitPriceMinor));
      return this;
    }

    public PlaceOrderCommandBuilder currency(String currency) {
      this.currency = currency;
      return this;
    }

    public PlaceOrderCommand build() {
      return new PlaceOrderCommand(customerId, items, currency);
    }
  }
}
