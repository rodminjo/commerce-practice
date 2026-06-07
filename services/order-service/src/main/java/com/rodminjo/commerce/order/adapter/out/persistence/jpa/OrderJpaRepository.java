package com.rodminjo.commerce.order.adapter.out.persistence.jpa;

import com.rodminjo.commerce.order.adapter.out.persistence.jpa.entity.OrderJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, UUID> {

    long countByCustomerId(String customerId);
}
