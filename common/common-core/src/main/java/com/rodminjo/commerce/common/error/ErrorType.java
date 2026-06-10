package com.rodminjo.commerce.common.error;

/**
 * 에러의 추상적 의미. 코어 레이어의 프레임워크 비의존 오류 분류.
 *
 * <p>HTTP 상태 코드가 아님. 웹 어댑터({@code common-infra})가 {@code HttpStatus}로 변환하며, gRPC·메시징 등 다른 진입점은 각자의
 * 방식으로 변환. 도메인·애플리케이션 레이어를 웹/HTTP 의존에서 분리.
 *
 * <p>다수의 도메인 {@link ErrorCode}가 하나의 타입을 공유 가능.
 */
public enum ErrorType {

  /** 잘못된 입력 또는 상태. → 400 */
  INVALID,
  /** 미인증. → 401 */
  UNAUTHORIZED,
  /** 인증됐으나 권한 없음. → 403 */
  FORBIDDEN,
  /** 리소스 없음. → 404 */
  NOT_FOUND,
  /** 현재 상태와 충돌(이미 처리됨, 잘못된 전이 등). → 409 */
  CONFLICT,
  /** 서버 측 오류. → 500 */
  INTERNAL
}
