package com.rodminjo.commerce.common.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(nativeQuery = true,
            value = "SELECT * FROM outbox WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED")
    List<OutboxEvent> lockPendingBatch(@Param("limit") int limit);

    // Simple derived queries (read) — JPA is the right fit for these.
    List<OutboxEvent> findByAggregateId(String aggregateId);

    long countByAggregateType(String aggregateType);
}
