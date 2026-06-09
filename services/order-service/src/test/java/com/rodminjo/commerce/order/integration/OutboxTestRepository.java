package com.rodminjo.commerce.order.integration;

import com.rodminjo.commerce.common.outbox.entity.OutboxEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Test-only repository for ad-hoc outbox lookups. These convenience finders deliberately do NOT
 * live on the production {@code OutboxRepository} (which exposes only the relay's locking query);
 * tests declare their own. Picked up by the service's
 * {@code @EnableJpaRepositories("com.rodminjo.commerce.order")}.
 */
public interface OutboxTestRepository extends JpaRepository<OutboxEvent, UUID> {

  List<OutboxEvent> findByAggregateId(String aggregateId);

  long countByAggregateType(String aggregateType);
}
