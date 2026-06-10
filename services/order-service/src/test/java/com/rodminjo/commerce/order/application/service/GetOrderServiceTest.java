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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link GetOrderService} 위임 동작 및 {@link OrderQueryPort#getById} 기본 메서드의 not-found 경로 단위 테스트. 통합
 * 테스트(해피 패스)에서 검증되지 않는 {@code orElseThrow(ORDER_NOT_FOUND)} 경로를 포함. {@code OrderQueryPort}는 함수형
 * 인터페이스({@code findById} 추상, {@code getById} 기본)이므로 Fake를 람다 한 줄로 구성.
 */
class GetOrderServiceTest {

  private static final UUID ORDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Nested
  @DisplayName("getOrder() — 조회 성공/실패")
  class GetOrder {

    @Test
    @DisplayName("주문이 존재하면 OrderView를 그대로 반환한다")
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
    @DisplayName("주문이 존재하지 않으면 DomainException(ORDER_NOT_FOUND)")
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
}
