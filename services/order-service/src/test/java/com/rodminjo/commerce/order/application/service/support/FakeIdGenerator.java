package com.rodminjo.commerce.order.application.service.support;

import com.rodminjo.commerce.common.id.IdGenerator;

/**
 * {@link IdGenerator} test double that returns a fixed, caller-supplied id for deterministic
 * assertions.
 */
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
