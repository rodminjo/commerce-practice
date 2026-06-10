package com.rodminjo.commerce.inventory.integration;

import com.rodminjo.commerce.common.outbox.entity.OutboxEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 테스트 전용 아웃박스 조회(프로덕션 {@code OutboxRepository}는 relay 쿼리만 노출). */
public interface OutboxTestRepository extends JpaRepository<OutboxEvent, UUID> {

  List<OutboxEvent> findByTopic(String topic);
}
