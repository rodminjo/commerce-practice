package com.rodminjo.commerce.order.adapter.in.web;

/**
 * {@code POST /api/orders/{id}/refund} 요청 바디(선택). {@code amountMinor} 생략 시 전체 환불, 값이 있으면 부분 환불 금액.
 * {@code reason} 생략 가능.
 */
public record RefundOrderRequest(Long amountMinor, String reason) {}
