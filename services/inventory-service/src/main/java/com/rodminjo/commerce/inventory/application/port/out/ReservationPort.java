package com.rodminjo.commerce.inventory.application.port.out;

import java.util.List;

/**
 * 주문별 예약 내역 추적 포트. 보상 경로({@code order.cancelled} — orderId만 포함)에서 정확한 수량 복구에 사용. 부분 멱등성 가드 겸용: 이미
 * 활성 예약이 있는 주문은 재예약하지 않음.
 */
public interface ReservationPort {

  void saveAll(String orderId, List<ReservedLine> lines);

  List<ReservedLine> findActive(String orderId);

  void markReleased(String orderId);

  record ReservedLine(String productId, int quantity) {}
}
