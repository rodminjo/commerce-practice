package com.rodminjo.commerce.payment.application.service.support;

import com.google.protobuf.Message;
import com.rodminjo.commerce.common.outbox.appender.OutboxAppender;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link OutboxAppender} 인메모리 테스트 대역. {@link #append} 호출을 {@link Appended} 레코드로 기록하여 Mockito 상호작용
 * 검증 대신 저장 상태(state)로 단언.
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

  /** {@link OutboxAppender#append} 단일 호출 캡처 레코드. */
  public record Appended(
      String aggregateType, String aggregateId, String topic, String partitionKey, Message event) {}
}
