package com.rodminjo.commerce.inventory.application.service;

import com.google.protobuf.Timestamp;
import com.rodminjo.commerce.common.error.DomainException;
import com.rodminjo.commerce.common.outbox.appender.OutboxAppender;
import com.rodminjo.commerce.common.time.ClockHolder;
import com.rodminjo.commerce.events.inventory.InventoryReserved;
import com.rodminjo.commerce.events.inventory.ReservedItem;
import com.rodminjo.commerce.inventory.application.port.in.ReserveStockUseCase;
import com.rodminjo.commerce.inventory.application.port.out.InventoryStockPort;
import com.rodminjo.commerce.inventory.application.port.out.ReservationPort;
import com.rodminjo.commerce.inventory.application.port.out.ReservationPort.ReservedLine;
import com.rodminjo.commerce.inventory.domain.InventoryErrorCode;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상위 서비스에서 전달된 주문의 재고 예약. 각 라인을 원자적 조건부 UPDATE({@code stock - reserved >= qty})로 처리. 영향 행 0 = 재고 부족
 * → 전체 롤백(전-또는-전무). 전 품목 성공 시 동일 트랜잭션 내 {@code InventoryReserved} 아웃박스 적재.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ReserveStockService implements ReserveStockUseCase {

  private final InventoryStockPort stockPort;
  private final ReservationPort reservationPort;
  private final OutboxAppender outboxAppender;
  private final ClockHolder clockHolder;

  @Override
  @Transactional
  public void reserve(ReserveStockCommand command) {
    // 부분 멱등성 가드(at-least-once): order.placed 재전달 시 이중 예약 방지.
    if (!reservationPort.findActive(command.orderId()).isEmpty()) {
      log.info("Order {} already has active reservations; skipping reserve", command.orderId());
      return;
    }

    for (ReserveStockCommand.Line line : command.items()) {
      if (!stockPort.exists(line.productId())) {
        throw new DomainException(
            InventoryErrorCode.PRODUCT_NOT_FOUND, "상품을 찾을 수 없습니다: " + line.productId());
      }
      int affected = stockPort.reserve(line.productId(), line.quantity());
      if (affected == 0) {
        throw new DomainException(
            InventoryErrorCode.INSUFFICIENT_STOCK, "재고가 부족합니다: " + line.productId());
      }
    }

    List<ReservedLine> reservedLines =
        command.items().stream()
            .map(line -> new ReservedLine(line.productId(), line.quantity()))
            .toList();
    reservationPort.saveAll(command.orderId(), reservedLines);

    outboxAppender.append(
        "Inventory",
        command.orderId(),
        "inventory.reserved",
        command.orderId(),
        buildEvent(command));
  }

  private InventoryReserved buildEvent(ReserveStockCommand command) {
    Instant now = clockHolder.now();
    Timestamp occurredAt =
        Timestamp.newBuilder().setSeconds(now.getEpochSecond()).setNanos(now.getNano()).build();

    InventoryReserved.Builder builder =
        InventoryReserved.newBuilder().setOrderId(command.orderId()).setOccurredAt(occurredAt);
    for (ReserveStockCommand.Line line : command.items()) {
      builder.addItems(
          ReservedItem.newBuilder()
              .setProductId(line.productId())
              .setQuantity(line.quantity())
              .build());
    }
    return builder.build();
  }
}
