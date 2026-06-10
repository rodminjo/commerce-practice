package com.rodminjo.commerce.order.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.rodminjo.commerce.common.error.DomainException;
import com.rodminjo.commerce.common.infra.time.SystemClockHolder;
import com.rodminjo.commerce.common.infra.web.GlobalExceptionHandler;
import com.rodminjo.commerce.order.application.port.in.CancelOrderUseCase;
import com.rodminjo.commerce.order.application.port.in.GetOrderUseCase;
import com.rodminjo.commerce.order.application.port.in.GetOrderUseCase.OrderView;
import com.rodminjo.commerce.order.application.port.in.PlaceOrderUseCase;
import com.rodminjo.commerce.order.config.SecurityConfig;
import com.rodminjo.commerce.order.domain.OrderErrorCode;
import com.rodminjo.commerce.order.domain.model.OrderStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = OrderController.class,
    excludeAutoConfiguration = {
      DataSourceAutoConfiguration.class,
      HibernateJpaAutoConfiguration.class,
      DataJpaRepositoriesAutoConfiguration.class
    })
@Import({
  SecurityConfig.class,
  OrderWebMapperImpl.class,
  GlobalExceptionHandler.class,
  SystemClockHolder.class
})
class OrderControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PlaceOrderUseCase placeOrderUseCase;

  @MockitoBean private GetOrderUseCase getOrderUseCase;

  @MockitoBean private CancelOrderUseCase cancelOrderUseCase;

  @MockitoBean private JwtDecoder jwtDecoder;

  @Nested
  @DisplayName("POST /api/orders — 주문 생성")
  class PlaceOrder {

    @Test
    @DisplayName("토큰 없이 요청 → 401 Unauthorized")
    void placeOrder_noToken_returns401() throws Exception {
      String body =
"""
{"customerId":"c1","items":[{"productId":"p1","quantity":1,"unitPriceMinor":1000}],"currency":"KRW"}
""";
      mockMvc
          .perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("유효한 JWT + 정상 요청 → 201 Created + orderId 반환")
    void placeOrder_withJwt_returns201() throws Exception {
      UUID orderId = UUID.randomUUID();
      when(placeOrderUseCase.place(any()))
          .thenReturn(new PlaceOrderUseCase.PlaceOrderResult(orderId));

      String body =
"""
{"customerId":"c1","items":[{"productId":"p1","quantity":1,"unitPriceMinor":1000}],"currency":"KRW"}
""";

      mockMvc
          .perform(
              post("/api/orders").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.orderId").value(orderId.toString()))
          .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("blank customerId → 400 VALIDATION_ERROR")
    void placeOrder_blankCustomerId_returns400() throws Exception {
      String body =
"""
{"customerId":"","items":[{"productId":"p1","quantity":1,"unitPriceMinor":1000}],"currency":"KRW"}
""";
      mockMvc
          .perform(
              post("/api/orders").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("빈 items 리스트 → 400 VALIDATION_ERROR")
    void placeOrder_emptyItems_returns400() throws Exception {
      String body =
          """
          {"customerId":"c1","items":[],"currency":"KRW"}
          """;
      mockMvc
          .perform(
              post("/api/orders").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("currency가 3글자 미만 → 400 VALIDATION_ERROR")
    void placeOrder_invalidCurrencyLength_returns400() throws Exception {
      String body =
"""
{"customerId":"c1","items":[{"productId":"p1","quantity":1,"unitPriceMinor":1000}],"currency":"KR"}
""";
      mockMvc
          .perform(
              post("/api/orders").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("유스케이스가 CONFLICT 예외 → 409 + INVALID_STATE_TRANSITION (ErrorType→HTTP 매핑)")
    void placeOrder_whenUseCaseThrowsConflict_returns409() throws Exception {
      when(placeOrderUseCase.place(any()))
          .thenThrow(new DomainException(OrderErrorCode.INVALID_STATE_TRANSITION));

      String body =
"""
{"customerId":"c1","items":[{"productId":"p1","quantity":1,"unitPriceMinor":1000}],"currency":"KRW"}
""";
      mockMvc
          .perform(
              post("/api/orders").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }
  }

  @Nested
  @DisplayName("GET /api/orders/{id} — 주문 조회")
  class GetOrder {

    @Test
    @DisplayName("존재하지 않는 주문 → 404 ORDER_NOT_FOUND")
    void getOrder_notFound_returns404() throws Exception {
      UUID id = UUID.randomUUID();
      when(getOrderUseCase.getOrder(id))
          .thenThrow(new DomainException(OrderErrorCode.ORDER_NOT_FOUND));

      mockMvc
          .perform(get("/api/orders/" + id).with(jwt()))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("존재하는 주문 → 200 + OrderDetailResponse 반환")
    void getOrder_found_returns200() throws Exception {
      UUID id = UUID.randomUUID();
      OrderView view =
          new OrderView(
              id,
              OrderStatus.PENDING,
              List.of(new OrderView.OrderItemView("p1", 1, 1000L)),
              1000L,
              "KRW");
      when(getOrderUseCase.getOrder(id)).thenReturn(view);

      mockMvc
          .perform(get("/api/orders/" + id).with(jwt()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.orderId").value(id.toString()))
          .andExpect(jsonPath("$.status").value("PENDING"))
          .andExpect(jsonPath("$.totalAmountMinor").value(1000));
    }
  }

  @Nested
  @DisplayName("POST /api/orders/{id}/cancel — 주문 취소")
  class CancelOrder {

    @Test
    @DisplayName("토큰 없이 요청 → 401 Unauthorized")
    void cancelOrder_noToken_returns401() throws Exception {
      mockMvc
          .perform(post("/api/orders/" + UUID.randomUUID() + "/cancel"))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("유효한 JWT + 정상 요청 → 200 CANCELLED")
    void cancelOrder_withJwt_returns200() throws Exception {
      UUID id = UUID.randomUUID();

      mockMvc
          .perform(post("/api/orders/" + id + "/cancel").with(jwt()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.orderId").value(id.toString()))
          .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("취소 불가 상태 주문 → 409 (도메인 가드)")
    void cancelOrder_whenNotCancellable_returns409() throws Exception {
      UUID id = UUID.randomUUID();
      org.mockito.Mockito.doThrow(new DomainException(OrderErrorCode.INVALID_STATE_TRANSITION))
          .when(cancelOrderUseCase)
          .cancel(any());

      mockMvc
          .perform(post("/api/orders/" + id + "/cancel").with(jwt()))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }
  }
}
