package com.rodminjo.commerce.common.error;

/**
 * 도메인에 귀속되지 않는 공통 에러 코드. 유효성 검사, 인증, 잘못된 요청, 서버 측 폴백 처리. 도메인별 코드는 각 서비스의 {@code <X>ErrorCode}에 위치.
 */
public enum CommonErrorCode implements ErrorCode {
  VALIDATION_ERROR("VALIDATION_ERROR", "입력값이 올바르지 않습니다", ErrorType.INVALID),
  MALFORMED_REQUEST("MALFORMED_REQUEST", "요청 형식이 올바르지 않습니다", ErrorType.INVALID),
  METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", "허용되지 않은 메서드입니다", ErrorType.INVALID),
  UNAUTHORIZED("UNAUTHORIZED", "인증이 필요합니다", ErrorType.UNAUTHORIZED),
  FORBIDDEN("FORBIDDEN", "권한이 없습니다", ErrorType.FORBIDDEN),
  INTERNAL_ERROR("INTERNAL_ERROR", "일시적인 오류가 발생했습니다", ErrorType.INTERNAL);

  private final String code;
  private final String defaultMessage;
  private final ErrorType type;

  CommonErrorCode(String code, String defaultMessage, ErrorType type) {
    this.code = code;
    this.defaultMessage = defaultMessage;
    this.type = type;
  }

  @Override
  public String code() {
    return code;
  }

  @Override
  public String defaultMessage() {
    return defaultMessage;
  }

  @Override
  public ErrorType type() {
    return type;
  }
}
