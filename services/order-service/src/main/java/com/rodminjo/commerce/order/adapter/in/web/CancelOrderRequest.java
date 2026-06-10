package com.rodminjo.commerce.order.adapter.in.web;

/** {@code POST /api/orders/{id}/cancel} 요청 바디(선택). {@code reason} 생략 가능. */
public record CancelOrderRequest(String reason) {}
