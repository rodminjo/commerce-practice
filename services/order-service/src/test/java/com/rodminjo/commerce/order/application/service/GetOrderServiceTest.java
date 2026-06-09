package com.rodminjo.commerce.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rodminjo.commerce.common.error.DomainException;
import com.rodminjo.commerce.order.application.port.in.GetOrderUseCase.OrderView;
import com.rodminjo.commerce.order.application.port.in.GetOrderUseCase.OrderView.OrderItemView;
import com.rodminjo.commerce.order.application.port.out.OrderQueryPort;
import com.rodminjo.commerce.order.domain.model.OrderStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link GetOrderService} delegation and the {@link OrderQueryPort#getById} default
 * method's not-found behavior — the {@code orElseThrow(ORDER_NOT_FOUND)} path that the integration
 * tests (happy-path only) never exercise. {@code OrderQueryPort} is a functional interface ({@code
 * findById} abstract, {@code getById} default), so the fake is a one-line lambda.
 */
class GetOrderServiceTest {

  private static final UUID ORDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Test
  @DisplayName("getOrder(): 존재하면 OrderView를 그대로 반환한다")
  void getOrder_found_returnsView() {
    OrderView view =
        new OrderView(
            ORDER_ID,
            OrderStatus.PENDING,
            List.of(new OrderItemView("p1", 1, 1000L)),
            1000L,
            "KRW");
    OrderQueryPort port = id -> Optional.of(view);
    GetOrderService service = new GetOrderService(port);

    assertThat(service.getOrder(ORDER_ID)).isSameAs(view);
  }

  @Test
  @DisplayName("getOrder(): 존재하지 않으면 DomainException(ORDER_NOT_FOUND)")
  void getOrder_notFound_throwsOrderNotFound() {
    OrderQueryPort port = id -> Optional.empty();
    GetOrderService service = new GetOrderService(port);

    assertThatThrownBy(() -> service.getOrder(ORDER_ID))
        .isInstanceOf(DomainException.class)
        .satisfies(
            ex -> {
              DomainException de = (DomainException) ex;
              assertThat(de.errorCode().code()).isEqualTo("ORDER_NOT_FOUND");
            });
  }
}
