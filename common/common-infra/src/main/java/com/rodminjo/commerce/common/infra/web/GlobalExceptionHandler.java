package com.rodminjo.commerce.common.infra.web;

import com.rodminjo.commerce.common.error.CommonErrorCode;
import com.rodminjo.commerce.common.error.DomainException;
import com.rodminjo.commerce.common.error.ErrorCode;
import com.rodminjo.commerce.common.error.ErrorType;
import com.rodminjo.commerce.common.time.ClockHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * 예외를 표준 {@link ErrorResponse}로 변환하는 단일 처리 지점. {@code common-infra}에 위치하여 컴포넌트 스캔으로 전 웹 서비스가 공유.
 * 서비스별 복사본 불필요.
 *
 * <p>{@link DomainException}만 {@link ErrorType}으로 HTTP 상태 결정. 프레임워크 예외는 HTTP 상태 직접 지정, 응답 {@code
 * code}에는 {@link CommonErrorCode} 사용.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final ClockHolder clockHolder;

  public GlobalExceptionHandler(ClockHolder clockHolder) {
    this.clockHolder = clockHolder;
  }

  @ExceptionHandler(DomainException.class)
  public ResponseEntity<ErrorResponse> handleDomain(
      DomainException ex, HttpServletRequest request) {
    ErrorCode errorCode = ex.errorCode();
    HttpStatus status = toHttpStatus(errorCode.type());
    log.warn(
        "DomainException [{}] {} - {}", errorCode.code(), request.getRequestURI(), ex.getMessage());
    return build(status, errorCode.code(), ex.getMessage(), List.of(), request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleBodyValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    List<ErrorResponse.FieldError> fieldErrors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
            .toList();
    log.warn("Validation failed {} - {}", request.getRequestURI(), fieldErrors);
    return build(HttpStatus.BAD_REQUEST, CommonErrorCode.VALIDATION_ERROR, fieldErrors, request);
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  public ResponseEntity<ErrorResponse> handleMethodValidation(
      HandlerMethodValidationException ex, HttpServletRequest request) {
    log.warn("Parameter validation failed {} - {}", request.getRequestURI(), ex.getMessage());
    return build(HttpStatus.BAD_REQUEST, CommonErrorCode.VALIDATION_ERROR, List.of(), request);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraintViolation(
      ConstraintViolationException ex, HttpServletRequest request) {
    List<ErrorResponse.FieldError> fieldErrors =
        ex.getConstraintViolations().stream()
            .map(v -> new ErrorResponse.FieldError(v.getPropertyPath().toString(), v.getMessage()))
            .toList();
    log.warn("Constraint violation {} - {}", request.getRequestURI(), fieldErrors);
    return build(HttpStatus.BAD_REQUEST, CommonErrorCode.VALIDATION_ERROR, fieldErrors, request);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadable(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
    log.warn("Malformed request body {} - {}", request.getRequestURI(), ex.getMessage());
    return build(HttpStatus.BAD_REQUEST, CommonErrorCode.MALFORMED_REQUEST, List.of(), request);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
    log.warn("Method not allowed {} - {}", request.getRequestURI(), ex.getMessage());
    return build(
        HttpStatus.METHOD_NOT_ALLOWED, CommonErrorCode.METHOD_NOT_ALLOWED, List.of(), request);
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ErrorResponse> handleAuthentication(
      AuthenticationException ex, HttpServletRequest request) {
    log.warn("Authentication failed {} - {}", request.getRequestURI(), ex.getMessage());
    return build(HttpStatus.UNAUTHORIZED, CommonErrorCode.UNAUTHORIZED, List.of(), request);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleAccessDenied(
      AccessDeniedException ex, HttpServletRequest request) {
    log.warn("Access denied {} - {}", request.getRequestURI(), ex.getMessage());
    return build(HttpStatus.FORBIDDEN, CommonErrorCode.FORBIDDEN, List.of(), request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
    // 클라이언트에 내부 상세 정보 노출 금지. 전체 스택 트레이스는 서버 로그에만 기록.
    log.error("Unhandled exception {}", request.getRequestURI(), ex);
    return build(
        HttpStatus.INTERNAL_SERVER_ERROR, CommonErrorCode.INTERNAL_ERROR, List.of(), request);
  }

  private HttpStatus toHttpStatus(ErrorType type) {
    return switch (type) {
      case INVALID -> HttpStatus.BAD_REQUEST;
      case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
      case FORBIDDEN -> HttpStatus.FORBIDDEN;
      case NOT_FOUND -> HttpStatus.NOT_FOUND;
      case CONFLICT -> HttpStatus.CONFLICT;
      case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
    };
  }

  private ResponseEntity<ErrorResponse> build(
      HttpStatus status,
      ErrorCode errorCode,
      List<ErrorResponse.FieldError> fieldErrors,
      HttpServletRequest request) {
    return build(status, errorCode.code(), errorCode.defaultMessage(), fieldErrors, request);
  }

  private ResponseEntity<ErrorResponse> build(
      HttpStatus status,
      String code,
      String message,
      List<ErrorResponse.FieldError> fieldErrors,
      HttpServletRequest request) {
    ErrorResponse body =
        new ErrorResponse(
            code,
            message,
            fieldErrors,
            clockHolder.now(),
            request.getRequestURI(),
            resolveTraceId());
    return ResponseEntity.status(status).body(body);
  }

  private String resolveTraceId() {
    String traceId = MDC.get("traceId");
    return (traceId != null && !traceId.isBlank())
        ? traceId
        : UUID.randomUUID().toString().substring(0, 8);
  }
}
