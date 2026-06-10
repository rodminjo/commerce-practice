package com.rodminjo.commerce.common.infra.web;

import java.time.Instant;
import java.util.List;

/**
 * 전 서비스 표준 에러 응답 본문. 성공 응답은 리소스 직접 반환(200/201/204), 에러만 이 봉투로 래핑.
 *
 * @param code 머신 가독 에러 코드({@code ErrorCode.code()})
 * @param message 사람 가독 메시지
 * @param fieldErrors 필드별 유효성 에러. 유효성 에러 외에는 빈 리스트(null 아님)
 * @param timestamp 에러 발생 시각({@code ClockHolder} 기준)
 * @param path 요청 경로
 * @param traceId 서버 로그와 공유하는 상관 ID
 */
public record ErrorResponse(
    String code,
    String message,
    List<FieldError> fieldErrors,
    Instant timestamp,
    String path,
    String traceId) {
  public record FieldError(String field, String reason) {}
}
