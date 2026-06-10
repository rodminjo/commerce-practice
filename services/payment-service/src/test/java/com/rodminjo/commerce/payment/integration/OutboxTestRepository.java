package com.rodminjo.commerce.payment.integration;

import com.rodminjo.commerce.common.outbox.entity.OutboxEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 테스트 전용 outbox 조회. 운영용 {@code OutboxRepository}는 relay 쿼리만 노출하므로 별도 정의. */
public interface OutboxTestRepository extends JpaRepository<OutboxEvent, UUID> {

  List<OutboxEvent> findByTopic(String topic);
}
