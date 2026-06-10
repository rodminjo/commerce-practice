package com.rodminjo.commerce.order.application.service.support;

import com.rodminjo.commerce.order.application.port.out.SaveOrderPort;
import com.rodminjo.commerce.order.domain.model.Order;
import java.util.ArrayList;
import java.util.List;

/** 인메모리 {@link SaveOrderPort} 테스트 대역. 저장된 주문을 기록하고 그대로 반환 — 실제 어댑터(영속화 후 동일 애그리거트 반환)와 동일한 동작. */
public class FakeSaveOrderPort implements SaveOrderPort {

  private final List<Order> saved = new ArrayList<>();

  @Override
  public Order save(Order order) {
    saved.add(order);
    return order;
  }

  /** {@link #save} 호출 순서대로 기록된 전체 주문 목록. */
  public List<Order> saved() {
    return saved;
  }
}
