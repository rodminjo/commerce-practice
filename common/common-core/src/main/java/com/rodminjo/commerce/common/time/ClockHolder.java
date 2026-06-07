package com.rodminjo.commerce.common.time;

import java.time.Instant;

/**
 * Shared contract for "current time" so code never calls {@link Instant#now()} directly.
 * Time becomes an injected dependency — tests substitute a fixed clock for deterministic results.
 *
 * <p>Lives in {@code common-core} (framework-free) so domain/application code can depend on it.
 * The system implementation lives in {@code common-infra}. Mirrors the {@code IdGenerator} split.
 */
@FunctionalInterface
public interface ClockHolder {

    Instant now();
}
