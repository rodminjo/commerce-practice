# Commerce MSA

주문 · 결제 · 재고를 분리한 **이벤트 기반 마이크로서비스** 학습 프로젝트.
Saga / Outbox / 멱등성 등 분산 트랜잭션 패턴을 실제로 구현하며 익히는 것이 목표.

---

## 기술 스택

| 영역 | 사용 기술 |
|---|---|
| 언어 / 런타임 | Java 25 (toolchain) |
| 프레임워크 | Spring Boot 4.0.6 |
| 빌드 | Gradle 9.5.1 멀티모듈 (version catalog, BOM) |
| 데이터베이스 | PostgreSQL 17 + pgvector (1 DB / N 스키마), Flyway |
| 캐시 · 락 | Redis 7 (Redisson) |
| 메시징 | Kafka (KRaft) + Schema Registry (Protobuf 3.25.5), Kafka-UI |
| 직렬화 | Protobuf (Confluent 8.0.0) |
| 인증 | Keycloak 26 (OIDC / JWT) · Spring Security OAuth2 Resource Server |
| API Gateway | Apache APISIX (+ etcd) |
| 관측성 | OpenTelemetry |
| 테스트 | JUnit 5, Testcontainers |
| 인프라 | Docker Compose |

---

## 서비스 구성

| 서비스 | 포트 | 역할 |
|---|---|---|
| order-service | 8081 | 주문 + Saga 오케스트레이터 |
| payment-service | 8082 | 결제 + 환불 (멱등) |
| inventory-service | 8083 | 재고 예약·복구 (원자적 UPDATE) |

게이트웨이(APISIX, `:9080`)가 JWT 검증, 각 서비스로 라우팅

---

## WHAT I LEARNED

- **Gradle 멀티모듈** — `include` 트리, version catalog, BOM import
- **1 DB N 스키마** 설계와 Flyway 스키마별 마이그레이션
- **Kafka 기초 → KRaft → Schema Registry** 호환성 / Protobuf 이벤트 계약
- **Keycloak OIDC** — realm / client / role, JWT 검증 흐름
- **APISIX** — route / upstream / plugin 3층 구조, etcd 연동
- **분산 트랜잭션** — Saga, Transactional Outbox, 멱등 컨슈머


---

## 실행

```bash
docker compose up -d                    # 인프라 컨테이너 일괄 기동
./infra/apisix/bootstrap-routes.sh      # APISIX 라우트 등록 (최초 1회)

# IDE에서 각 Spring Boot 서비스 직접 실행

curl http://localhost:9080/api/orders/health   # → 401 (JWT 없음, OIDC가 차단)
```
