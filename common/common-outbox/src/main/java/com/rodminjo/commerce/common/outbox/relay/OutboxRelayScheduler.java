package com.rodminjo.commerce.common.outbox.relay;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Scheduling trigger for the outbox relay. Kept as a separate bean from {@link OutboxRelay} on
 * purpose: the call to {@link OutboxRelay#publishBatch()} crosses a bean boundary, so it goes
 * through the Spring proxy and the {@code @Transactional} advice is applied. (A {@code @Scheduled}
 * method calling a {@code @Transactional} method on the <em>same</em> bean would self-invoke and
 * silently skip the transaction.)
 *
 * <p>Requires {@code @EnableScheduling} on the consuming application. Poll interval comes from
 * {@code outbox.relay.poll-interval-ms} (see {@link OutboxRelayProperties}).
 */
@RequiredArgsConstructor
public class OutboxRelayScheduler {

  private final OutboxRelay relay;

  @Scheduled(fixedDelayString = "${outbox.relay.poll-interval-ms:1000}")
  public void poll() {
    relay.publishBatch();
  }
}
