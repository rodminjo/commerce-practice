package com.rodminjo.commerce.order.application.port.in;

/**
 * Order Saga 오케스트레이터 인바운드 포트. 메시징 어댑터({@code SagaEventsConsumer})는 구체 서비스가 아닌 이 인터페이스에 의존 — 재고 컨슈머가
 * {@code ReserveStockUseCase}/{@code ReleaseStockUseCase}에 의존하는 방식과 동일. 3개 핸들러가 하나의 응집된 상태 머신을
 * 구성하므로 이벤트별로 분리하지 않고 단일 유스케이스에 위치.
 */
public interface OrderSagaUseCase {

  /** 재고 예약 완료 → 주문 총액 결제 요청. */
  void onInventoryReserved(String orderId);

  /** 결제 완료 → 주문 확정. */
  void onPaymentCompleted(String orderId);

  /** 결제 실패 → 주문 취소 및 보상 이벤트(재고 해제) 발행. */
  void onPaymentFailed(String orderId, String reason);
}
