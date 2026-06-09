package com.rodminjo.commerce.common.outbox.appender;

import com.google.protobuf.Message;

public interface OutboxAppender {

  void append(
      String aggregateType, String aggregateId, String topic, String partitionKey, Message event);
}
