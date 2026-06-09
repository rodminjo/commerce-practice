package com.rodminjo.commerce.order.adapter.in.web;

import com.rodminjo.commerce.order.application.port.in.GetOrderUseCase;
import com.rodminjo.commerce.order.application.port.in.PlaceOrderUseCase;
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
}
