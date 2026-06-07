package com.rodminjo.commerce.order.adapter.in.web;

import com.rodminjo.commerce.common.error.DomainException;
import com.rodminjo.commerce.common.infra.time.SystemClockHolder;
import com.rodminjo.commerce.common.infra.web.GlobalExceptionHandler;
import com.rodminjo.commerce.order.application.port.in.GetOrderUseCase;
import com.rodminjo.commerce.order.application.port.in.GetOrderUseCase.OrderView;
import com.rodminjo.commerce.order.application.port.in.PlaceOrderUseCase;
import com.rodminjo.commerce.order.config.SecurityConfig;
import com.rodminjo.commerce.order.domain.OrderErrorCode;
import com.rodminjo.commerce.order.domain.model.OrderStatus;
import org.junit.jupiter.api.DisplayName;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = OrderController.class,
        excludeAutoConfiguration = {
                DataSourceAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class,
                DataJpaRepositoriesAutoConfiguration.class
        }
)
@Import({SecurityConfig.class, OrderWebMapperImpl.class, GlobalExceptionHandler.class, SystemClockHolder.class})
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlaceOrderUseCase placeOrderUseCase;

    @MockitoBean
    private GetOrderUseCase getOrderUseCase;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("POST /api/orders without token → 401")
    void placeOrder_noToken_returns401() throws Exception {
        String body = """
                {"customerId":"c1","items":[{"productId":"p1","quantity":1,"unitPriceMinor":1000}],"currency":"KRW"}
                """;
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/orders with valid JWT → 201 + orderId")
    void placeOrder_withJwt_returns201() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(placeOrderUseCase.place(any()))
                .thenReturn(new PlaceOrderUseCase.PlaceOrderResult(orderId));

        String body = """
                {"customerId":"c1","items":[{"productId":"p1","quantity":1,"unitPriceMinor":1000}],"currency":"KRW"}
                """;

        mockMvc.perform(post("/api/orders")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /api/orders/{id} when order not found → 404")
    void getOrder_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(getOrderUseCase.getOrder(id))
                .thenThrow(new DomainException(OrderErrorCode.ORDER_NOT_FOUND));

        mockMvc.perform(get("/api/orders/" + id)
                        .with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/orders/{id} when found → 200 with view")
    void getOrder_found_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        OrderView view = new OrderView(
                id,
                OrderStatus.PENDING,
                List.of(new OrderView.OrderItemView("p1", 1, 1000L)),
                1000L,
                "KRW"
        );
        when(getOrderUseCase.getOrder(id)).thenReturn(view);

        mockMvc.perform(get("/api/orders/" + id)
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(id.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmountMinor").value(1000));
    }
}
