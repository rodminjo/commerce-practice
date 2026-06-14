package com.rodminjo.commerce.common.outbox.inbox;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/**
 * Kafka 메시지의 {@code x-event-id} 헤더(UUID toString UTF-8 bytes)를 {@link UUID}로 파싱하는 유틸. 릴레이({@code
 * OutboxRelay})가 적재하는 헤더와 짝을 이루며, 멱등 컨슈머의 dedup 키 추출에 사용한다.
 *
 * <p>헤더 부재는 프로듀서 측 계약 위반(릴레이가 항상 적재)이므로 조용히 넘기지 않고 명시적으로 예외를 던지고 로그를 남긴다.
 */
@Slf4j
public final class EventIdHeader {

  /** Kafka 헤더 키. */
  public static final String HEADER = "x-event-id";

  private EventIdHeader() {}

  /**
   * {@code x-event-id} 헤더 bytes를 UUID로 파싱. 헤더가 없거나 비어 있으면 {@link IllegalStateException}을 던지고 로그를
   * 남긴다.
   */
  public static UUID parse(byte[] header) {
    if (header == null || header.length == 0) {
      log.error("필수 헤더 '{}' 누락 — 멱등 dedup 키를 추출할 수 없음", HEADER);
      throw new IllegalStateException("필수 헤더 '" + HEADER + "' 누락");
    }
    String raw = new String(header, StandardCharsets.UTF_8);
    try {
      return UUID.fromString(raw);
    } catch (IllegalArgumentException e) {
      // 잘못된 형식도 헤더 계약 위반이므로 부재와 동일하게 IllegalStateException으로 통일 처리.
      log.error("헤더 '{}' 형식 오류 — UUID 파싱 실패: '{}'", HEADER, raw);
      throw new IllegalStateException("헤더 '" + HEADER + "' 형식 오류: " + raw, e);
    }
  }
}
