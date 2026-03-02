package com.pkware.micronaut.security.grpc;

/**
 * Metric names and tag constants for {@link GrpcSecurityInterceptor}.
 *
 * <p>These are stable, documented constants. Dashboard queries and alerting rules
 * can rely on them across library upgrades.
 *
 * <p>Counter: {@value AUTH_ATTEMPTS} — tags: {@value TAG_OUTCOME} ({@value OUTCOME_SUCCESS} /
 * {@value OUTCOME_FAILED}), {@value TAG_REASON}.
 *
 * <p>Timer: {@value AUTHENTICATION_DURATION} — authentication latency including
 * token extraction, validation, and JWKS key fetch on cache miss.
 */
final class GrpcSecurityMetrics {

  /** Counter for gRPC authentication attempts. */
  static final String AUTH_ATTEMPTS = "grpc.security.auth.attempts";

  /** Timer for the full authentication pipeline (token extraction + validation). */
  static final String AUTHENTICATION_DURATION = "grpc.security.authentication";

  /** Tag name for the outcome of an auth operation. */
  static final String TAG_OUTCOME = "outcome";

  /** Tag name for the reason an operation failed. */
  static final String TAG_REASON = "reason";

  /** Tag value for successful operations. */
  static final String OUTCOME_SUCCESS = "success";

  /** Tag value for failed operations. */
  static final String OUTCOME_FAILED = "failed";

  /** Reason tag value for successful authentication. Maintains consistent tag cardinality. */
  static final String REASON_NONE = "none";

  /** Reason tag value when the {@code SecurityRule} chain returned REJECTED or all UNKNOWN. */
  static final String REASON_REJECTED = "rejected";

  /** Reason tag value when the called method is not in the {@link GrpcSecuredMethodRegistry}. */
  static final String REASON_METHOD_NOT_ALLOWED = "method_not_allowed";

  private GrpcSecurityMetrics() {}
}
