package com.rodminjo.commerce.common.id;

/**
 * Shared output contract for identifier generation. Abstracted behind an interface so the strategy
 * is injectable and swappable in tests (a fixed/fake generator yields deterministic ids).
 *
 * <p>Lives in {@code common-core} (framework-free) so application/domain code can depend on it
 * without pulling infrastructure. Concrete strategies live in {@code common-infra}.
 *
 * <p>Conventions:
 *
 * <ul>
 *   <li>{@code IdGenerator<UUID>} — aggregates that need a public/external or distributed id
 *       (default).
 *   <li>{@code IdGenerator<Long>} — entities that do not need a UUID and benefit from sort/index
 *       optimization (time-sorted long via TSID).
 * </ul>
 *
 * Spring resolves the concrete bean by the generic type argument.
 *
 * @param <T> the id type (e.g. {@code UUID} or {@code Long})
 */
public interface IdGenerator<T> {

  T newId();
}
