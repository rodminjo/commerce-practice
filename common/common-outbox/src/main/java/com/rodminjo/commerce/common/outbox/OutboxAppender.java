package com.rodminjo.commerce.common.outbox;

import com.google.protobuf.Message;

public interface OutboxAppender {

    void append(String aggregateType, String aggregateId, String topic, String partitionKey, Message event);
}
