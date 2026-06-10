package com.rodminjo.commerce.common.id;

/**
 * 식별자 생성 공통 계약. 인터페이스로 추상화하여 전략 주입 및 테스트용 고정 ID 교체 지원.
 *
 * <p>{@code common-core}(프레임워크 비의존)에 위치하여 도메인·애플리케이션 코드가 인프라 없이 의존 가능. 구체 전략은 {@code common-infra}에
 * 위치.
 *
 * <p>사용 규칙:
 *
 * <ul>
 *   <li>{@code IdGenerator<UUID>} — 외부 공개 또는 분산 ID가 필요한 애그리게이트(기본).
 *   <li>{@code IdGenerator<Long>} — UUID 불필요, 정렬·인덱스 최적화가 필요한 엔티티(TSID 기반 시간 정렬 Long).
 * </ul>
 *
 * Spring은 제네릭 타입 인수로 구체 빈 결정.
 *
 * @param <T> ID 타입(예: {@code UUID} 또는 {@code Long})
 */
public interface IdGenerator<T> {

  T newId();
}
