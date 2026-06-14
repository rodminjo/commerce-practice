package com.rodminjo.commerce.common.outbox.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EventIdHeaderTest {

  @Test
  @DisplayName("정상 UUID bytes → UUID 파싱")
  void parsesValidUuid() {
    UUID expected = UUID.randomUUID();
    byte[] header = expected.toString().getBytes(StandardCharsets.UTF_8);

    assertThat(EventIdHeader.parse(header)).isEqualTo(expected);
  }

  @Test
  @DisplayName("헤더 부재(null) → IllegalStateException")
  void throwsWhenNull() {
    assertThatThrownBy(() -> EventIdHeader.parse(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(EventIdHeader.HEADER);
  }

  @Test
  @DisplayName("헤더 부재(빈 배열) → IllegalStateException")
  void throwsWhenEmpty() {
    assertThatThrownBy(() -> EventIdHeader.parse(new byte[0]))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(EventIdHeader.HEADER);
  }

  @Test
  @DisplayName("형식 오류(UUID 아님) → IllegalStateException으로 통일 래핑")
  void wrapsMalformedUuid() {
    byte[] header = "not-a-uuid".getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> EventIdHeader.parse(header))
        .isInstanceOf(IllegalStateException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class);
  }
}
