package com.rodminjo.commerce.common.infra.id;

import com.rodminjo.commerce.common.id.IdGenerator;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** UUID id strategy — for aggregates needing a public/external or distributed identifier. */
@Component
public class UuidIdGenerator implements IdGenerator<UUID> {

    @Override
    public UUID newId() {
        return UUID.randomUUID();
    }
}
