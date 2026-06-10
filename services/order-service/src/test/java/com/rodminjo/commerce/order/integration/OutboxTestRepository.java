package com.rodminjo.commerce.order.integration;

import com.rodminjo.commerce.common.outbox.entity.OutboxEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 임시 outbox 조회용 테스트 전용 리포지토리. 편의 조회 메서드는 의도적으로 프로덕션 {@code OutboxRepository}(릴레이 잠금 쿼리만 노출)에 두지 않고
 * 테스트가 직접 선언. 서비스의 {@code @EnableJpaRepositories("com.rodminjo.commerce.order")}에 의해 스캔됨.
 */
public interface OutboxTestRepository extends JpaRepository<OutboxEvent, UUID> {

  List<OutboxEvent> findByAggregateId(String aggregateId);

  long countByAggregateType(String aggregateType);
}
