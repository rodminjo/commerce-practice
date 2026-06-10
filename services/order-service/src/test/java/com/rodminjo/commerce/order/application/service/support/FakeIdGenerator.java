package com.rodminjo.commerce.order.application.service.support;

import com.rodminjo.commerce.common.id.IdGenerator;

/** 결정론적 단언을 위해 호출자가 지정한 고정 id를 반환하는 {@link IdGenerator} 테스트 대역. */
public class FakeIdGenerator<T> implements IdGenerator<T> {

  private final T id;

  public FakeIdGenerator(T id) {
    this.id = id;
  }

  @Override
  public T newId() {
    return id;
  }
}
