package com.rodminjo.commerce.order.adapter.out.persistence.jpa.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.rodminjo.commerce.order.domain.model.Order;
import com.rodminjo.commerce.order.domain.model.OrderLineItem;
import com.rodminjo.commerce.order.domain.model.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * JPA 엔티티 매핑 라운드트립 순수 단위 테스트(Spring/DB 불필요). {@code fromDomain}과 {@code toDomain} 양방향 보장: 도메인 → 엔티티
 * → 도메인 변환 시 전 필드 보존. {@code toDomain}은 현재 읽기 경로(MyBatis)에서 미사용이나 이 테스트로 정확성 유지.
 */
class OrderJpaEntityMappingTest {

  private static final UUID ORDER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final Instant CREATED_AT = Instant.parse("2024-01-01T00:00:00Z");

  @Nested
  @DisplayName("fromDomain / toDomain 라운드트립")
  class RoundTrip {

    @Test
    @DisplayName("Order → OrderJpaEntity → Order 라운드트립이 모든 필드를 보존한다")
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
      // domain → entity → domain 왕복에서 도메인이 모델링하는 모든 필드가 보존되는지 한 줄로 검증.
      assertThat(restored).usingRecursiveComparison().isEqualTo(order);
    }
  }
}
