package com.pkware.micronaut.grpc.authorization

import io.micronaut.context.annotation.ConfigurationProperties

/**
 * Configuration for [GrpcAuthorizationInterceptor].
 *
 * `anonymousServices` lists gRPC service names (the portion of a full method name before
 * the `/`, e.g. `grpc.health.v1.Health`) whose methods are reachable without a principal.
 * Intended for third-party services that cannot carry `@RolesAllowed`. Combined with
 * deny-by-default, nothing is anonymous unless explicitly listed.
 *
 *
 * An interface: Micronaut generates the binding implementation. The getter must be abstract —
 * a `default` method would be treated as a fixed implementation and the property would never
 * bind. Returns `null` when unset; the interceptor treats that as the empty list.
 */
@ConfigurationProperties("grpc.authorization")
public interface GrpcAuthorizationConfiguration {
  /** gRPC service names reachable without authentication, or `null` if none configured.  */
  public val anonymousServices: List<String>?
}
