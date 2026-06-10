package com.rodminjo.commerce.common.outbox.repository;

import com.rodminjo.commerce.common.outbox.entity.OutboxEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 아웃박스 영속성 인터페이스. 의도적으로 최소화: 릴레이 프로덕션 쿼리만 포함. 임시 조회(애그리게이트 ID/타입 기반)는 테스트 관심사이며 이 공유 인터페이스에 누출 금지.
 * 테스트는 별도 리포지토리에 선언.
 */
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

  @Query(
      nativeQuery = true,
      value =
          "SELECT * FROM outbox WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT :limit FOR"
              + " UPDATE SKIP LOCKED")
  List<OutboxEvent> lockPendingBatch(@Param("limit") int limit);
}
