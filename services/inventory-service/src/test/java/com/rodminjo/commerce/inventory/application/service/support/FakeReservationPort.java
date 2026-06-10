package com.rodminjo.commerce.inventory.application.service.support;

import com.rodminjo.commerce.inventory.application.port.out.ReservationPort;
import java.util.ArrayList;
import java.util.List;

/** {@link ReservationPort}의 인메모리 Fake. 주문별 예약 내역을 상태와 함께 추적. */
public class FakeReservationPort implements ReservationPort {

  private final List<Entry> entries = new ArrayList<>();

  private static final class Entry {
    final String orderId;
    final ReservedLine line;
    boolean released;

    Entry(String orderId, ReservedLine line) {
      this.orderId = orderId;
      this.line = line;
    }
  }

  /** 지정 주문에 활성(RESERVED) 예약 초기화. */
  public void seedActive(String orderId, ReservedLine... lines) {
    for (ReservedLine line : lines) {
      entries.add(new Entry(orderId, line));
    }
  }

  @Override
  public void saveAll(String orderId, List<ReservedLine> lines) {
    for (ReservedLine line : lines) {
      entries.add(new Entry(orderId, line));
    }
  }

  @Override
  public List<ReservedLine> findActive(String orderId) {
    List<ReservedLine> result = new ArrayList<>();
    for (Entry entry : entries) {
      if (entry.orderId.equals(orderId) && !entry.released) {
        result.add(entry.line);
      }
    }
    return result;
  }

  @Override
  public void markReleased(String orderId) {
    for (Entry entry : entries) {
      if (entry.orderId.equals(orderId)) {
        entry.released = true;
      }
    }
  }

  /** 상태 무관, 주문에 저장된 전체 예약 조회(검증용). */
  public List<ReservedLine> saved(String orderId) {
    List<ReservedLine> result = new ArrayList<>();
    for (Entry entry : entries) {
      if (entry.orderId.equals(orderId)) {
        result.add(entry.line);
      }
    }
    return result;
  }
}
