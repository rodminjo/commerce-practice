package com.rodminjo.commerce.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.rodminjo.commerce.common.id.IdGenerator;
import com.rodminjo.commerce.common.time.ClockHolder;
import com.rodminjo.commerce.events.order.OrderPlaced;
import com.rodminjo.commerce.order.application.port.in.PlaceOrderUseCase.PlaceOrderCommand;
import com.rodminjo.commerce.order.application.port.in.PlaceOrderUseCase.PlaceOrderCommand.OrderItemCommand;
import com.rodminjo.commerce.order.application.port.in.PlaceOrderUseCase.PlaceOrderResult;
import com.rodminjo.commerce.order.application.service.support.FakeIdGenerator;
import com.rodminjo.commerce.order.application.service.support.FakeOutboxAppender;
import com.rodminjo.commerce.order.application.service.support.FakeOutboxAppender.Appended;
import com.rodminjo.commerce.order.application.service.support.FakeSaveOrderPort;
import com.rodminjo.commerce.order.domain.model.Order;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PlaceOrderServiceTest {

  private static final UUID ORDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final Instant FIXED_NOW = Instant.parse("2024-01-01T00:00:00Z");

  private final FakeSaveOrderPort saveOrderPort = new FakeSaveOrderPort();
  private final FakeOutboxAppender outboxAppender = new FakeOutboxAppender();
  private final IdGenerator<UUID> idGenerator = new FakeIdGenerator<>(ORDER_ID);
  private final ClockHolder clockHolder = () -> FIXED_NOW;

  private PlaceOrderService placeOrderService;

  @BeforeEach
  void setUp() {
    placeOrderService =
        new PlaceOrderService(saveOrderPort, outboxAppender, idGenerator, clockHolder);
  }

  private static PlaceOrderCommand command() {
    return new PlaceOrderCommand(
        "customer-1",
        List.of(new OrderItemCommand("p1", 2, 500L), new OrderItemCommand("p2", 1, 1000L)),
        "KRW");
  }

  @Nested
  @DisplayName("place() — 주문 생성 및 outbox 적재")
  class Place {

    @Test
    @DisplayName("생성된 id로 저장하고 그 orderId를 결과로 반환한다")
    void place_savesAndReturnsGeneratedOrderId() {
      PlaceOrderResult result = placeOrderService.place(command());

      assertThat(result.orderId()).isEqualTo(ORDER_ID);
      assertThat(saveOrderPort.saved()).hasSize(1);
      Order saved = saveOrderPort.saved().get(0);
      assertThat(saved.getId()).isEqualTo(ORDER_ID);
    }

    @Test
    @DisplayName("OrderPlaced 이벤트가 모든 필드를 정확히 담아 outbox에 append된다")
    void place_appendsOrderPlacedEventWithAllFields() {
      placeOrderService.place(command());

      assertThat(outboxAppender.appended()).hasSize(1);
      Appended appended = outboxAppender.appended().get(0);
      assertThat(appended.aggregateType()).isEqualTo("Order");
      assertThat(appended.aggregateId()).isEqualTo(ORDER_ID.toString());
      assertThat(appended.topic()).isEqualTo("order.placed");
      assertThat(appended.partitionKey()).isEqualTo(ORDER_ID.toString());

      assertThat(appended.event()).isInstanceOf(OrderPlaced.class);
      OrderPlaced event = (OrderPlaced) appended.event();

      assertThat(event.getOrderId()).isEqualTo(ORDER_ID.toString());
      assertThat(event.getCustomerId()).isEqualTo("customer-1");
      assertThat(event.getCurrency()).isEqualTo("KRW");
      assertThat(event.getTotalAmountMinor()).isEqualTo(2000L); // 2*500 + 1*1000
      assertThat(event.getOccurredAt().getSeconds()).isEqualTo(FIXED_NOW.getEpochSecond());
      assertThat(event.getOccurredAt().getNanos()).isEqualTo(FIXED_NOW.getNano());

      assertThat(event.getItemsList()).hasSize(2);
      assertThat(event.getItems(0).getProductId()).isEqualTo("p1");
      assertThat(event.getItems(0).getQuantity()).isEqualTo(2);
      assertThat(event.getItems(0).getUnitPriceMinor()).isEqualTo(500L);
      assertThat(event.getItems(1).getProductId()).isEqualTo("p2");
      assertThat(event.getItems(1).getQuantity()).isEqualTo(1);
      assertThat(event.getItems(1).getUnitPriceMinor()).isEqualTo(1000L);
    }
  }
}
