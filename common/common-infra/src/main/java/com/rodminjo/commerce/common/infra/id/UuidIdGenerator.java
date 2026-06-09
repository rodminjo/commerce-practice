package com.rodminjo.commerce.common.infra.id;

import com.rodminjo.commerce.common.id.IdGenerator;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** UUID id strategy — for aggregates needing a public/external or distributed identifier. */
@Component
public class UuidIdGenerator implements IdGenerator<UUID> {

  @Override
  public UUID newId() {
    return UUID.randomUUID();
  }
}
