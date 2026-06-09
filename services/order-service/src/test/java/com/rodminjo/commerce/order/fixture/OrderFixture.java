package com.rodminjo.commerce.order.fixture;

import com.rodminjo.commerce.order.application.port.in.PlaceOrderUseCase.PlaceOrderCommand;
import com.rodminjo.commerce.order.application.port.in.PlaceOrderUseCase.PlaceOrderCommand.OrderItemCommand;
import com.rodminjo.commerce.order.domain.model.Order;
import com.rodminjo.commerce.order.domain.model.OrderLineItem;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Test data builders for the Order aggregate and its inbound {@link PlaceOrderCommand}.
 *
 * <p>Defaults produce a valid PENDING order; a test overrides only the field it cares about (e.g.
 * {@code OrderFixture.anOrder().currency("USD").build()}).
 *
 * <p>Do NOT route invalid-input cases (null/blank/bad currency) through here — pass those args
 * inline at the call site so the offending value stays visible in the test.
 */
public final class OrderFixture {

  public static final Instant FIXED_NOW = Instant.parse("2024-01-01T00:00:00Z");
  public static final String DEFAULT_CUSTOMER_ID = "customer-1";
  public static final String DEFAULT_PRODUCT_ID = "product-A";
  public static final String DEFAULT_CURRENCY = "KRW";

  private OrderFixture() {}

  /** A single valid line item: 1 x {@value #DEFAULT_PRODUCT_ID} @ 1000. */
  public static OrderLineItem lineItem() {
    return OrderLineItem.of(DEFAULT_PRODUCT_ID, 1, 1000L);
  }

  /** A valid PENDING {@link Order} with default fields. */
  public static Order order() {
    return anOrder().build();
  }

  public static OrderBuilder anOrder() {
    return new OrderBuilder();
  }

  /** A valid {@link PlaceOrderCommand} with default fields. */
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

    /** Replace the line items with a single item. */
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
