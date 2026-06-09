package com.rodminjo.commerce.common.outbox.repository;

import com.rodminjo.commerce.common.outbox.entity.OutboxEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence seam for the outbox. Intentionally narrow: only the relay's production query lives
 * here. Ad-hoc lookups (by aggregate id/type) are test concerns and must not leak into this shared
 * interface — tests declare their own repository for those.
 */
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

  @Query(
      nativeQuery = true,
      value =
          "SELECT * FROM outbox WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT :limit FOR"
              + " UPDATE SKIP LOCKED")
  List<OutboxEvent> lockPendingBatch(@Param("limit") int limit);
}
