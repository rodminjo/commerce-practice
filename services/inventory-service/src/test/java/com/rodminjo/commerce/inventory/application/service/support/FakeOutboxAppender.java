package com.rodminjo.commerce.inventory.application.service.support;

import com.google.protobuf.Message;
import com.rodminjo.commerce.common.outbox.appender.OutboxAppender;
import java.util.ArrayList;
import java.util.List;

/** {@link OutboxAppender}의 인메모리 Fake. 상태 검증을 위해 모든 append 호출을 기록. */
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
