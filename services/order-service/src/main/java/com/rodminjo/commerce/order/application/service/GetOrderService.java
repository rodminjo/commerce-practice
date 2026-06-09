package com.rodminjo.commerce.order.application.service;

import com.rodminjo.commerce.order.application.port.in.GetOrderUseCase;
import com.rodminjo.commerce.order.application.port.out.OrderQueryPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class GetOrderService implements GetOrderUseCase {

  private final OrderQueryPort orderQueryPort;

  @Override
  public OrderView getOrder(UUID orderId) {
    return orderQueryPort.getById(orderId);
  }
}
