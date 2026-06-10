package com.rodminjo.commerce.order.adapter.in.web;

/** Optional body for {@code POST /api/orders/{id}/cancel}; {@code reason} may be omitted. */
public record CancelOrderRequest(String reason) {}
