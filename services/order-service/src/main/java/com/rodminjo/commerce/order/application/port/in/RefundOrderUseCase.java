package com.rodminjo.commerce.order.application.port.in;

import java.util.UUID;

/**
 * 사용자 발생 환불 인바운드 포트. CONFIRMED/COMPLETED 주문을 REFUNDED로 전이시키고 {@code refund.requested}를 발행하여 결제 서비스가
 * 실제 환불을 수행하도록 한다.
 */
public interface RefundOrderUseCase {

  void refund(RefundOrderCommand command);

  /**
   * 환불 명령. {@code amountMinor}가 {@code null}이면 주문 총액 전체 환불, 값이 있으면 부분 환불 금액.
   *
   * @param orderId 환불 대상 주문
   * @param amountMinor 부분 환불 금액(minor 단위). null이면 전체 환불
   * @param reason 환불 사유(선택)
   */
  record RefundOrderCommand(UUID orderId, Long amountMinor, String reason) {}
}
