package com.rodminjo.commerce.inventory.domain;

import com.rodminjo.commerce.common.error.ErrorCode;
import com.rodminjo.commerce.common.error.ErrorType;

public enum InventoryErrorCode implements ErrorCode {
  PRODUCT_NOT_FOUND("PRODUCT_NOT_FOUND", "상품을 찾을 수 없습니다", ErrorType.NOT_FOUND),
  INSUFFICIENT_STOCK("INSUFFICIENT_STOCK", "재고가 부족합니다", ErrorType.CONFLICT),
  INVALID_INVENTORY("INVALID_INVENTORY", "유효하지 않은 재고 작업입니다", ErrorType.INVALID);

  private final String code;
  private final String defaultMessage;
  private final ErrorType type;

  InventoryErrorCode(String code, String defaultMessage, ErrorType type) {
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
