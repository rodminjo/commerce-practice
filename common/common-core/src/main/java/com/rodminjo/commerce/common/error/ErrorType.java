package com.rodminjo.commerce.common.error;

/**
 * Abstract meaning of an error — the core's framework-free notion of "what kind of failure this
 * is".
 *
 * <p>Deliberately NOT an HTTP status. The web adapter ({@code common-infra}) translates each type
 * to an {@code HttpStatus}; other entry points (gRPC, messaging) may translate it their own way.
 * This keeps the domain/application layers free of any web/HTTP dependency.
 *
 * <p>Many domain {@link ErrorCode}s may share one type (HTTP has ~6 statuses, domains have many
 * codes).
 */
public enum ErrorType {

  /** Invalid input or state. → 400 */
  INVALID,
  /** Not authenticated. → 401 */
  UNAUTHORIZED,
  /** Authenticated but not permitted. → 403 */
  FORBIDDEN,
  /** Resource does not exist. → 404 */
  NOT_FOUND,
  /** Conflicts with the current state (already processed, illegal transition, ...). → 409 */
  CONFLICT,
  /** Server-side failure. → 500 */
  INTERNAL
}
