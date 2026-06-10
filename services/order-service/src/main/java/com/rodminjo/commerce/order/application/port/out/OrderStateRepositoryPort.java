package com.rodminjo.commerce.order.application.port.out;

import com.rodminjo.commerce.order.domain.model.Order;
import java.util.Optional;
import java.util.UUID;

/**
 * Saga 및 취소 유스케이스가 사용하는 주문 조회·상태 갱신 포트. {@link SaveOrderPort}(신규 주문+항목 삽입)와 분리: {@link #update}는 기존
 * 주문의 상태 전이만 영속화하며 라인 아이템을 재삽입하지 않음.
 */
public interface OrderStateRepositoryPort {

  Optional<Order> findById(UUID id);

  void update(Order order);
}
