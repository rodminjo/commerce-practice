package com.rodminjo.commerce.common.outbox.relay;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the outbox relay, bound from {@code outbox.relay.*}.
 *
 * <p>Replaces scattered {@code @Value} lookups so the relay depends on a single typed object.
 * Defaults are applied here (and re-asserted in the setters) so a missing or non-positive value
 * never produces a degenerate poll/batch size.
 */
@Getter
@ConfigurationProperties(prefix = "outbox.relay")
public class OutboxRelayProperties {

  /**
   * Scheduler poll interval in milliseconds (also referenced by the {@code @Scheduled}
   * placeholder).
   */
  private long pollIntervalMs = 1000L;

  /** Max number of PENDING events fetched and published per poll. */
  private int batchSize = 100;

  public void setPollIntervalMs(long pollIntervalMs) {
    this.pollIntervalMs = pollIntervalMs > 0 ? pollIntervalMs : 1000L;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize > 0 ? batchSize : 100;
  }
}
