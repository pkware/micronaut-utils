package com.pkware.micronaut.grpc.authorization

/**
 * Stable metric name and tag constants for [GrpcAuthorizationInterceptor]. Dashboards and
 * alerts may rely on these across library upgrades.
 */
internal object GrpcAuthorizationMetrics {
  const val AUTH_ATTEMPTS = "grpc.authorization.attempts"
  const val AUTHENTICATION_DURATION = "grpc.authorization.resolve"

  const val TAG_OUTCOME = "outcome"
  const val TAG_REASON = "reason"

  const val OUTCOME_SUCCESS = "success"
  const val OUTCOME_FAILED = "failed"

  const val REASON_NONE = "none"
  const val REASON_REJECTED = "rejected"
  const val REASON_METHOD_NOT_ALLOWED = "method_not_allowed"
}
