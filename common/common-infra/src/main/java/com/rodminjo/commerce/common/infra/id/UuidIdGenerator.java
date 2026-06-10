package com.rodminjo.commerce.common.infra.id;

import com.rodminjo.commerce.common.id.IdGenerator;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** UUID ID 전략. 외부 공개 또는 분산 식별자가 필요한 애그리게이트용. */
@Component
public class UuidIdGenerator implements IdGenerator<UUID> {

  @Override
  public UUID newId() {
    return UUID.randomUUID();
  }
}
