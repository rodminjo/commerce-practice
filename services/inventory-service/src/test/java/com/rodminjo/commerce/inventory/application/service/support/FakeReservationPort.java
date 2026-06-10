package com.rodminjo.commerce.inventory.application.service.support;

import com.rodminjo.commerce.inventory.application.port.out.ReservationPort;
import java.util.ArrayList;
import java.util.List;

/** In-memory fake of {@link ReservationPort}. Tracks reservations per order with a status. */
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

  /** Arranges active (RESERVED) reservations for an order. */
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

  /** All reservations ever saved for an order, regardless of status (for assertions). */
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
