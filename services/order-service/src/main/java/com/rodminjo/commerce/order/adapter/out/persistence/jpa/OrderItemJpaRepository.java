package com.rodminjo.commerce.order.adapter.out.persistence.jpa;

import com.rodminjo.commerce.order.adapter.out.persistence.jpa.entity.OrderItemJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemJpaRepository extends JpaRepository<OrderItemJpaEntity, Long> {

  List<OrderItemJpaEntity> findByOrderId(UUID orderId);
}
