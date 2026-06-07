package com.rodminjo.commerce.common.infra.id;

import com.rodminjo.commerce.common.id.IdGenerator;
import io.hypersistence.tsid.TSID;
import org.springframework.stereotype.Component;

/**
 * TSID (time-sorted) long id strategy — for entities that don't need a UUID and benefit from
 * sortable, index-friendly keys. 64-bit, monotonic-ish, distributed-safe.
 */
@Component
public class TsidIdGenerator implements IdGenerator<Long> {

    @Override
    public Long newId() {
        return TSID.Factory.getTsid().toLong();
    }
}
