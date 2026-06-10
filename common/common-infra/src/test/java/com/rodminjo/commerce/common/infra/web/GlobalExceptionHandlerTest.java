package com.rodminjo.commerce.common.infra.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rodminjo.commerce.common.error.DomainException;
import com.rodminjo.commerce.common.error.ErrorCode;
import com.rodminjo.commerce.common.error.ErrorType;
import com.rodminjo.commerce.common.time.ClockHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

  private static final Instant FIXED = Instant.parse("2026-06-07T10:00:00Z");

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    ClockHolder fixedClock = () -> FIXED;
    mockMvc =
        MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler(fixedClock))
            .build();
  }

  @Nested
  @DisplayName("DomainException 처리")
  class DomainExceptionHandling {

    @Test
    @DisplayName("NOT_FOUND → 404 + 표준 스키마")
    void domainNotFound_returns404WithSchema() throws Exception {
      mockMvc
          .perform(get("/test/not-found"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("TEST_NOT_FOUND"))
          .andExpect(jsonPath("$.message").value("없습니다"))
          .andExpect(jsonPath("$.fieldErrors").isArray())
          .andExpect(jsonPath("$.fieldErrors").isEmpty())
          .andExpect(jsonPath("$.timestamp").value("2026-06-07T10:00:00Z"))
          .andExpect(jsonPath("$.path").value("/test/not-found"))
          .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("CONFLICT → 409")
    void domainConflict_returns409() throws Exception {
      mockMvc
          .perform(get("/test/conflict"))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("TEST_CONFLICT"));
    }
  }

  @Nested
  @DisplayName("유효성 검사 오류 처리")
  class ValidationErrorHandling {

    @Test
    @DisplayName("@Valid 위반 → 400 VALIDATION_ERROR + fieldErrors")
    void validationError_returns400WithFieldErrors() throws Exception {
      mockMvc
          .perform(
              post("/test/validate")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"name\":\"\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
          .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
          .andExpect(jsonPath("$.fieldErrors[0].reason").isNotEmpty());
    }

    @Test
    @DisplayName("깨진 JSON → 400 MALFORMED_REQUEST")
    void malformedJson_returns400() throws Exception {
      mockMvc
          .perform(
              post("/test/validate").contentType(MediaType.APPLICATION_JSON).content("{not json"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }
  }

  @Nested
  @DisplayName("접근 제어 오류 처리")
  class AccessControlErrorHandling {

    @Test
    @DisplayName("AccessDeniedException → 403 FORBIDDEN")
    void accessDenied_returns403() throws Exception {
      mockMvc
          .perform(get("/test/forbidden"))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
  }

  @Nested
  @DisplayName("미처리 예외 처리")
  class UnhandledExceptionHandling {

    @Test
    @DisplayName("처리 안 된 예외 → 500 INTERNAL_ERROR, 내부 메시지 숨김")
    void uncaught_returns500AndHidesMessage() throws Exception {
      mockMvc
          .perform(get("/test/boom"))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
          .andExpect(
              jsonPath("$.message")
                  .value(
                      org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SECRET"))));
    }
  }

  @RestController
  @RequestMapping("/test")
  static class TestController {

    @GetMapping("/not-found")
    void notFound() {
      throw new DomainException(TestErrorCode.TEST_NOT_FOUND);
    }

    @GetMapping("/conflict")
    void conflict() {
      throw new DomainException(TestErrorCode.TEST_CONFLICT);
    }

    @PostMapping("/validate")
    void validate(@Valid @RequestBody ValidateRequest request) {
      // no-op; validation happens before entry
    }

    @GetMapping("/forbidden")
    void forbidden() {
      throw new AccessDeniedException("denied");
    }

    @GetMapping("/boom")
    void boom() {
      throw new RuntimeException("SECRET internal detail");
    }
  }

  record ValidateRequest(@NotBlank String name) {}

  enum TestErrorCode implements ErrorCode {
    TEST_NOT_FOUND("TEST_NOT_FOUND", "없습니다", ErrorType.NOT_FOUND),
    TEST_CONFLICT("TEST_CONFLICT", "충돌", ErrorType.CONFLICT);

    private final String code;
    private final String defaultMessage;
    private final ErrorType type;

    TestErrorCode(String code, String defaultMessage, ErrorType type) {
      this.code = code;
      this.defaultMessage = defaultMessage;
      this.type = type;
    }

    @Override
    public String code() {
      return code;
    }

    @Override
    public String defaultMessage() {
      return defaultMessage;
    }

    @Override
    public ErrorType type() {
      return type;
    }
  }
}
