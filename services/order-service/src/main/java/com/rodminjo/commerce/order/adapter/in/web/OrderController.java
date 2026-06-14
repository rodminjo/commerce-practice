package com.rodminjo.commerce.order.adapter.in.web;

import com.rodminjo.commerce.order.application.port.in.CancelOrderUseCase;
import com.rodminjo.commerce.order.application.port.in.CancelOrderUseCase.CancelOrderCommand;
import com.rodminjo.commerce.order.application.port.in.GetOrderUseCase;
import com.rodminjo.commerce.order.application.port.in.PlaceOrderUseCase;
import com.rodminjo.commerce.order.application.port.in.RefundOrderUseCase;
import com.rodminjo.commerce.order.application.port.in.RefundOrderUseCase.RefundOrderCommand;
import com.rodminjo.commerce.order.domain.model.OrderStatus;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/orders")
public class OrderController {

  private final PlaceOrderUseCase placeOrderUseCase;
  private final GetOrderUseCase getOrderUseCase;
  private final CancelOrderUseCase cancelOrderUseCase;
  private final RefundOrderUseCase refundOrderUseCase;
  private final OrderWebMapper orderWebMapper;

  @PostMapping
  public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
    UUID orderId = placeOrderUseCase.place(orderWebMapper.toCommand(request)).orderId();
    OrderResponse body = new OrderResponse(orderId, OrderStatus.PENDING);
    return ResponseEntity.created(URI.create("/api/orders/" + orderId)).body(body);
  }

  @GetMapping("/{id}")
  public ResponseEntity<OrderDetailResponse> getOrder(@PathVariable UUID id) {
    OrderDetailResponse body = orderWebMapper.toResponse(getOrderUseCase.getOrder(id));
    return ResponseEntity.ok(body);
  }

  @PostMapping("/{id}/cancel")
  public ResponseEntity<OrderResponse> cancelOrder(
      @PathVariable UUID id, @RequestBody(required = false) CancelOrderRequest request) {
    String reason = request == null ? null : request.reason();
    cancelOrderUseCase.cancel(new CancelOrderCommand(id, reason));
    return ResponseEntity.ok(new OrderResponse(id, OrderStatus.CANCELLED));
  }

  @PostMapping("/{id}/refund")
  public ResponseEntity<OrderResponse> refundOrder(
      @PathVariable UUID id, @RequestBody(required = false) RefundOrderRequest request) {
    Long amountMinor = request == null ? null : request.amountMinor();
    String reason = request == null ? null : request.reason();
    refundOrderUseCase.refund(new RefundOrderCommand(id, amountMinor, reason));
    return ResponseEntity.ok(new OrderResponse(id, OrderStatus.REFUNDED));
  }
}
