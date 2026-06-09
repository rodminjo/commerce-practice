package com.rodminjo.commerce.order.adapter.out.persistence.jpa;

import com.rodminjo.commerce.order.adapter.out.persistence.jpa.entity.OrderJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, UUID> {

  long countByCustomerId(String customerId);
}
