package com.rodminjo.commerce.order.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PlaceOrderRequest(
    @NotBlank String customerId,
    @NotEmpty @Valid List<OrderItemRequest> items,
    @NotBlank @Size(min = 3, max = 3) String currency) {

  public record OrderItemRequest(@NotBlank String productId, int quantity, long unitPriceMinor) {}
}
