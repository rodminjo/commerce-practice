package com.rodminjo.commerce.payment.adapter.out.persistence.jpa;

import com.rodminjo.commerce.payment.adapter.out.persistence.jpa.entity.PaymentJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentJpaRepository extends JpaRepository<PaymentJpaEntity, UUID> {

  List<PaymentJpaEntity> findByOrderId(String orderId);
}
