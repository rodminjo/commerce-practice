package com.rodminjo.commerce.payment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 모의 결제 동작 스위치. 기본값은 항상 성공. {@code payment.simulate.fail-for-amount}에 양수 minor 금액을 설정하면 해당 금액의 결제에
 * 대해 {@code PaymentFailed}를 강제 발생 — Saga 보상 테스트용 결정적 훅.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "payment.simulate")
public class PaymentSimulationProperties {

  /** 실패 대상 금액(minor 단위). {@code -1}(기본값)이면 항상 성공. */
  private long failForAmount = -1;

  public boolean shouldFail(long amountMinor) {
    return failForAmount >= 0 && amountMinor == failForAmount;
  }
}
