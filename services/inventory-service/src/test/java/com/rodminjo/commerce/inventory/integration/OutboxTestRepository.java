package com.rodminjo.commerce.inventory.integration;

import com.rodminjo.commerce.common.outbox.entity.OutboxEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Test-only outbox lookups (production {@code OutboxRepository} exposes only the relay query). */
public interface OutboxTestRepository extends JpaRepository<OutboxEvent, UUID> {

  List<OutboxEvent> findByTopic(String topic);
}
