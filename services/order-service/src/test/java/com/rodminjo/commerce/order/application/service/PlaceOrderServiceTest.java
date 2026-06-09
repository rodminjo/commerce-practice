package com.rodminjo.commerce.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.protobuf.Message;
import com.rodminjo.commerce.common.id.IdGenerator;
import com.rodminjo.commerce.common.outbox.appender.OutboxAppender;
import com.rodminjo.commerce.common.time.ClockHolder;
import com.rodminjo.commerce.events.order.OrderPlaced;
import com.rodminjo.commerce.order.application.port.in.PlaceOrderUseCase.PlaceOrderCommand;
import com.rodminjo.commerce.order.application.port.in.PlaceOrderUseCase.PlaceOrderCommand.OrderItemCommand;
import com.rodminjo.commerce.order.application.port.in.PlaceOrderUseCase.PlaceOrderResult;
import com.rodminjo.commerce.order.application.port.out.SaveOrderPort;
import com.rodminjo.commerce.order.domain.model.Order;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaceOrderServiceTest {

  private static final UUID ORDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final Instant FIXED_NOW = Instant.parse("2024-01-01T00:00:00Z");

  @Mock private SaveOrderPort saveOrderPort;
  @Mock private OutboxAppender outboxAppender;
  @Mock private IdGenerator<UUID> idGenerator;
  @Mock private ClockHolder clockHolder;

  @InjectMocks private PlaceOrderService placeOrderService;

  private static PlaceOrderCommand command() {
    return new PlaceOrderCommand(
        "customer-1",
        List.of(new OrderItemCommand("p1", 2, 500L), new OrderItemCommand("p2", 1, 1000L)),
        "KRW");
  }

  @Test
  @DisplayName("place(): 생성된 id로 저장하고 그 orderId를 결과로 반환한다")
  void place_savesAndReturnsGeneratedOrderId() {
    when(idGenerator.newId()).thenReturn(ORDER_ID);
    when(clockHolder.now()).thenReturn(FIXED_NOW);
    when(saveOrderPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

    PlaceOrderResult result = placeOrderService.place(command());

    assertThat(result.orderId()).isEqualTo(ORDER_ID);
    verify(saveOrderPort).save(any(Order.class));
  }

  @Test
  @DisplayName("place(): OrderPlaced 이벤트가 모든 필드를 정확히 담아 outbox에 append된다")
  void place_appendsOrderPlacedEventWithAllFields() {
    when(idGenerator.newId()).thenReturn(ORDER_ID);
    when(clockHolder.now()).thenReturn(FIXED_NOW);
    when(saveOrderPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

    placeOrderService.place(command());

    ArgumentCaptor<Message> eventCaptor = ArgumentCaptor.forClass(Message.class);
    verify(outboxAppender)
        .append(
            eq("Order"),
            eq(ORDER_ID.toString()),
            eq("order.placed"),
            eq(ORDER_ID.toString()),
            eventCaptor.capture());

    assertThat(eventCaptor.getValue()).isInstanceOf(OrderPlaced.class);
    OrderPlaced event = (OrderPlaced) eventCaptor.getValue();

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
