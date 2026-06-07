package com.rodminjo.commerce.order.adapter.out.persistence.jpa;

import com.rodminjo.commerce.order.adapter.out.persistence.jpa.entity.OrderItemJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderItemJpaRepository extends JpaRepository<OrderItemJpaEntity, Long> {

    List<OrderItemJpaEntity> findByOrderId(UUID orderId);
}
