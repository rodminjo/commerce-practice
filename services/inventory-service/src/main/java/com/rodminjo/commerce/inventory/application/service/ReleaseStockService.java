package com.rodminjo.commerce.inventory.application.service;

import com.google.protobuf.Timestamp;
import com.rodminjo.commerce.common.outbox.appender.OutboxAppender;
import com.rodminjo.commerce.common.time.ClockHolder;
import com.rodminjo.commerce.events.inventory.InventoryReleased;
import com.rodminjo.commerce.inventory.application.port.in.ReleaseStockUseCase;
import com.rodminjo.commerce.inventory.application.port.out.InventoryStockPort;
import com.rodminjo.commerce.inventory.application.port.out.ReservationPort;
import com.rodminjo.commerce.inventory.application.port.out.ReservationPort.ReservedLine;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compensation: releases the stock an order reserved when that order is cancelled (user cancel or
 * payment failure). Looks the reserved quantities up by orderId (the {@code order.cancelled} event
 * carries none), releases each atomically, marks the reservations RELEASED, and appends {@code
 * InventoryReleased}. Idempotent: an order with no active reservations is a no-op.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ReleaseStockService implements ReleaseStockUseCase {

  private final InventoryStockPort stockPort;
  private final ReservationPort reservationPort;
  private final OutboxAppender outboxAppender;
  private final ClockHolder clockHolder;

  @Override
  @Transactional
  public void release(ReleaseStockCommand command) {
    List<ReservedLine> active = reservationPort.findActive(command.orderId());
    if (active.isEmpty()) {
      log.info("Order {} has no active reservations; skipping release", command.orderId());
      return;
    }

    for (ReservedLine line : active) {
      stockPort.release(line.productId(), line.quantity());
    }
    reservationPort.markReleased(command.orderId());

    outboxAppender.append(
        "Inventory",
        command.orderId(),
        "inventory.released",
        command.orderId(),
        buildEvent(command));
  }

  private InventoryReleased buildEvent(ReleaseStockCommand command) {
    Instant now = clockHolder.now();
    Timestamp occurredAt =
        Timestamp.newBuilder().setSeconds(now.getEpochSecond()).setNanos(now.getNano()).build();
    return InventoryReleased.newBuilder()
        .setOrderId(command.orderId())
        .setReason(command.reason() == null ? "" : command.reason())
        .setOccurredAt(occurredAt)
        .build();
  }
}
