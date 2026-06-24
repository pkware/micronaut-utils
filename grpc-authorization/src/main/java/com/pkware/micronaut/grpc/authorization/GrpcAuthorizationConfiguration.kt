package com.pkware.micronaut.grpc.authorization;

import io.micronaut.context.annotation.ConfigurationProperties;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Configuration for {@link GrpcAuthorizationInterceptor}.
 *
 * <p>{@code anonymousServices} lists gRPC service names (the portion of a full method name before
 * the {@code /}, e.g. {@code grpc.health.v1.Health}) whose methods are reachable without a principal.
 * Intended for third-party services that cannot carry {@code @RolesAllowed}. Combined with
 * deny-by-default, nothing is anonymous unless explicitly listed.
 *
 * <p>An interface: Micronaut generates the binding implementation. The getter must be abstract —
 * a {@code default} method would be treated as a fixed implementation and the property would never
 * bind. Returns {@code null} when unset; the interceptor treats that as the empty list.
 */
@ConfigurationProperties("grpc.authorization")
public interface GrpcAuthorizationConfiguration {

  /** gRPC service names reachable without authentication, or {@code null} if none configured. */
  @Nullable List<String> getAnonymousServices();
}
