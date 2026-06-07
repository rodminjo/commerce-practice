package com.rodminjo.commerce.order.adapter.in.web;

import com.rodminjo.commerce.order.domain.model.OrderStatus;

import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        OrderStatus status
) {}
