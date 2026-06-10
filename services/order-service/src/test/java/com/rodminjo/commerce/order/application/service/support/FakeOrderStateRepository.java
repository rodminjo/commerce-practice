package com.rodminjo.commerce.order.application.service.support;

import com.rodminjo.commerce.order.application.port.out.OrderStateRepositoryPort;
import com.rodminjo.commerce.order.domain.model.Order;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 인메모리 {@link OrderStateRepositoryPort} 테스트 대역. id 키 맵에 주문을 저장하며, {@link #update}는 저장된 인스턴스를 교체.
 * {@link #seed(Order)}로 사전 상태를 준비.
 */
public class FakeOrderStateRepository implements OrderStateRepositoryPort {

  private final Map<UUID, Order> store = new HashMap<>();

  /** Arrange 헬퍼: 서비스가 조회할 수 있도록 기존 주문을 미리 적재. */
  public void seed(Order order) {
    store.put(order.getId(), order);
  }

  @Override
  public Optional<Order> findById(UUID id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public void update(Order order) {
    store.put(order.getId(), order);
  }
}
