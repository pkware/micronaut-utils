package com.pkware.micronaut.grpc.authorization;

/**
 * Stable metric name and tag constants for {@link GrpcAuthorizationInterceptor}. Dashboards and
 * alerts may rely on these across library upgrades.
 */
final class GrpcAuthorizationMetrics {

  static final String AUTH_ATTEMPTS = "grpc.authorization.attempts";
  static final String AUTHENTICATION_DURATION = "grpc.authorization.resolve";

  static final String TAG_OUTCOME = "outcome";
  static final String TAG_REASON = "reason";

  static final String OUTCOME_SUCCESS = "success";
  static final String OUTCOME_FAILED = "failed";

  static final String REASON_NONE = "none";
  static final String REASON_REJECTED = "rejected";
  static final String REASON_METHOD_NOT_ALLOWED = "method_not_allowed";

  private GrpcAuthorizationMetrics() {}
}
