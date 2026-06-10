package com.rodminjo.commerce.payment.domain;

import com.rodminjo.commerce.common.error.ErrorCode;
import com.rodminjo.commerce.common.error.ErrorType;

public enum PaymentErrorCode implements ErrorCode {
  INVALID_PAYMENT("INVALID_PAYMENT", "유효하지 않은 결제입니다", ErrorType.INVALID),
  INVALID_PAYMENT_STATE("INVALID_PAYMENT_STATE", "허용되지 않은 결제 상태 전이입니다", ErrorType.CONFLICT);

  private final String code;
  private final String defaultMessage;
  private final ErrorType type;

  PaymentErrorCode(String code, String defaultMessage, ErrorType type) {
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
