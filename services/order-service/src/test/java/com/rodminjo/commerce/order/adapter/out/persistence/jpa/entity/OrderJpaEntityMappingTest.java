package com.rodminjo.commerce.order.adapter.out.persistence.jpa.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.rodminjo.commerce.order.domain.model.Order;
import com.rodminjo.commerce.order.domain.model.OrderLineItem;
import com.rodminjo.commerce.order.domain.model.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pure mapping round-trip for the JPA entities (no Spring/DB). Guards both {@code fromDomain} and
 * {@code toDomain}: domain -> entity -> domain must preserve every field. {@code toDomain} is not
 * wired into the read path yet (MyBatis serves reads) — this test keeps it correct until it is.
 */
class OrderJpaEntityMappingTest {

  private static final UUID ORDER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final Instant CREATED_AT = Instant.parse("2024-01-01T00:00:00Z");

  @Test
  @DisplayName("Order -> OrderJpaEntity -> Order 라운드트립이 모든 필드를 보존한다")
  void roundTrip_preservesAllFields() {
    List<OrderLineItem> items =
        List.of(OrderLineItem.of("p1", 2, 500L), OrderLineItem.of("p2", 1, 1000L));
    Order order = Order.place(ORDER_ID, "customer-1", items, "KRW", CREATED_AT);

    OrderJpaEntity entity = OrderJpaEntity.fromDomain(order);

    assertThat(entity.getId()).isEqualTo(ORDER_ID);
    assertThat(entity.getCustomerId()).isEqualTo("customer-1");
    assertThat(entity.getStatus()).isEqualTo(OrderStatus.PENDING);
    assertThat(entity.getTotalAmountMinor()).isEqualTo(2000L);
    assertThat(entity.getCurrency()).isEqualTo("KRW");
    assertThat(entity.getCreatedAt()).isEqualTo(CREATED_AT);

    List<OrderItemJpaEntity> itemEntities =
        order.getItems().stream()
            .map(item -> OrderItemJpaEntity.fromDomain(order.getId(), item))
            .toList();
    assertThat(itemEntities).allSatisfy(it -> assertThat(it.getOrderId()).isEqualTo(ORDER_ID));

    Order restored = entity.toDomain(itemEntities);

    // restored와 order는 같은 Order 타입 → equals() 없이도 items 리스트까지 재귀 비교.
    // domain → entity → domain 왕복에서 도메인이 모델링하는 모든 필드가 보존되는지 한 줄로 검증한다.
    assertThat(restored).usingRecursiveComparison().isEqualTo(order);
  }
}
