package com.rodminjo.commerce.payment.application.service.support;

import com.google.protobuf.Message;
import com.rodminjo.commerce.common.outbox.appender.OutboxAppender;
import java.util.ArrayList;
import java.util.List;

/**
 * In-memory {@link OutboxAppender} test double. Records every {@link #append} call as an {@link
 * Appended} record so tests assert on stored events (state) instead of Mockito interactions.
 */
public class FakeOutboxAppender implements OutboxAppender {

  private final List<Appended> appended = new ArrayList<>();

  @Override
  public void append(
      String aggregateType, String aggregateId, String topic, String partitionKey, Message event) {
    appended.add(new Appended(aggregateType, aggregateId, topic, partitionKey, event));
  }

  /** All recorded appends, in call order. */
  public List<Appended> appended() {
    return appended;
  }

  /** A single captured {@link OutboxAppender#append} invocation. */
  public record Appended(
      String aggregateType, String aggregateId, String topic, String partitionKey, Message event) {}
}
