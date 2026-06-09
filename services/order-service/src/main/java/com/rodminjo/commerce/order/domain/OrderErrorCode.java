package com.rodminjo.commerce.order.domain;

import com.rodminjo.commerce.common.error.ErrorCode;
import com.rodminjo.commerce.common.error.ErrorType;

public enum OrderErrorCode implements ErrorCode {
  ORDER_NOT_FOUND("ORDER_NOT_FOUND", "주문을 찾을 수 없습니다", ErrorType.NOT_FOUND),
  INVALID_ORDER("INVALID_ORDER", "유효하지 않은 주문입니다", ErrorType.INVALID),
  INVALID_STATE_TRANSITION("INVALID_STATE_TRANSITION", "허용되지 않은 상태 전이입니다", ErrorType.CONFLICT);

  private final String code;
  private final String defaultMessage;
  private final ErrorType type;

  OrderErrorCode(String code, String defaultMessage, ErrorType type) {
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
