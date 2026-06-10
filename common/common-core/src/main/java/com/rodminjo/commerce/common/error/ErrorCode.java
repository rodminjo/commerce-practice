package com.rodminjo.commerce.common.error;

public interface ErrorCode {
  String code();

  String defaultMessage();

  /** 에러의 추상적 의미. 웹 어댑터가 HTTP 상태 코드로 변환. */
  ErrorType type();
}
