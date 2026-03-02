package com.pkware.micronaut.security.grpc;

import io.grpc.Context;
import io.micronaut.security.authentication.Authentication;

/**
 * gRPC {@link Context} keys set by {@link GrpcSecurityInterceptor}.
 *
 * <p>After successful authentication, the interceptor stores the validated
 * {@link Authentication} in the gRPC {@link Context}. Downstream interceptors
 * and service implementations read it via {@code AUTHENTICATION.get()}.
 */
public final class GrpcSecurityContext {

  /**
   * The authenticated caller's identity, or {@code null} if the request
   * was unauthenticated (e.g. health checks, {@code @Secured(IS_ANONYMOUS)} methods).
   */
  public static final Context.Key<Authentication> AUTHENTICATION =
      Context.key("grpc.security.authentication");

  private GrpcSecurityContext() {}
}
