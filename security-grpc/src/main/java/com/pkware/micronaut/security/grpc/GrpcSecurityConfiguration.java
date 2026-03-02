package com.pkware.micronaut.security.grpc;

import io.micronaut.context.annotation.ConfigurationProperties;

import java.util.List;
import java.util.Set;

/**
 * Configuration for {@link GrpcSecurityInterceptor}.
 *
 * <p>Prefix: {@code micronaut.security.grpc}.
 */
@ConfigurationProperties("micronaut.security.grpc")
public class GrpcSecurityConfiguration {

  private Set<String> unauthenticatedServices = Set.of();

  /**
   * gRPC service names that bypass authentication entirely.
   *
   * <p>Matched against the service portion of the full method name (before the {@code /}).
   * Typically used for third-party services like {@code grpc.health.v1.Health} that cannot
   * carry {@code @Secured} annotations.
   *
   * <p>Example configuration:
   * <pre>
   * micronaut.security.grpc.unauthenticated-services[0]=grpc.health.v1.Health
   * </pre>
   *
   * @return service names that bypass authentication.
   */
  public Set<String> getUnauthenticatedServices() {
    return unauthenticatedServices;
  }

  /**
   * Sets the unauthenticated services.
   *
   * @param unauthenticatedServices service names that bypass authentication.
   */
  public void setUnauthenticatedServices(List<String> unauthenticatedServices) {
    this.unauthenticatedServices = Set.copyOf(unauthenticatedServices);
  }
}
