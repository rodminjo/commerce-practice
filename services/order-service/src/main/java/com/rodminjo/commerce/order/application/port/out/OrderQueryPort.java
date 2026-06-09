package com.rodminjo.commerce.order.application.port.out;

import com.rodminjo.commerce.common.error.DomainException;
import com.rodminjo.commerce.order.application.port.in.GetOrderUseCase.OrderView;
import com.rodminjo.commerce.order.domain.OrderErrorCode;
import java.util.Optional;
import java.util.UUID;

public interface OrderQueryPort {

  Optional<OrderView> findById(UUID id);

  default OrderView getById(UUID id) {
    return findById(id).orElseThrow(() -> new DomainException(OrderErrorCode.ORDER_NOT_FOUND));
  }
}
