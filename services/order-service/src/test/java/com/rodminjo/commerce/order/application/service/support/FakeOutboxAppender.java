package com.rodminjo.commerce.order.application.service.support;

import com.google.protobuf.Message;
import com.rodminjo.commerce.common.outbox.appender.OutboxAppender;
import java.util.ArrayList;
import java.util.List;

/**
 * 인메모리 {@link OutboxAppender} 테스트 대역. 모든 {@link #append} 호출을 {@link Appended} 레코드로 기록하여 테스트가
 * Mockito 인터랙션 대신 저장 상태(state)로 단언할 수 있도록 함.
 */
public class FakeOutboxAppender implements OutboxAppender {

  private final List<Appended> appended = new ArrayList<>();

  @Override
  public void append(
      String aggregateType, String aggregateId, String topic, String partitionKey, Message event) {
    appended.add(new Appended(aggregateType, aggregateId, topic, partitionKey, event));
  }

  /** 호출 순서대로 기록된 전체 append 목록. */
  public List<Appended> appended() {
    return appended;
  }

  /** 단일 {@link OutboxAppender#append} 호출 캡처 레코드. */
  public record Appended(
      String aggregateType, String aggregateId, String topic, String partitionKey, Message event) {}
}
