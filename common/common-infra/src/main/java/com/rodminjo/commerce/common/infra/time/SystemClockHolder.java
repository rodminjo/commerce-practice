package com.rodminjo.commerce.common.infra.time;

import com.rodminjo.commerce.common.time.ClockHolder;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class SystemClockHolder implements ClockHolder {

    @Override
    public Instant now() {
        return Instant.now();
    }
}
