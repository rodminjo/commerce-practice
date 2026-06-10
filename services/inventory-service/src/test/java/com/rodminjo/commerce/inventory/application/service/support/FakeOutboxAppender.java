package com.rodminjo.commerce.inventory.application.service.support;

import com.google.protobuf.Message;
import com.rodminjo.commerce.common.outbox.appender.OutboxAppender;
import java.util.ArrayList;
import java.util.List;

/** In-memory fake of {@link OutboxAppender} that records every append for state assertions. */
public class FakeOutboxAppender implements OutboxAppender {

  public record Appended(
      String aggregateType, String aggregateId, String topic, String partitionKey, Message event) {}

  private final List<Appended> appended = new ArrayList<>();

  @Override
  public void append(
      String aggregateType, String aggregateId, String topic, String partitionKey, Message event) {
    appended.add(new Appended(aggregateType, aggregateId, topic, partitionKey, event));
  }

  public List<Appended> appended() {
    return appended;
  }
}
