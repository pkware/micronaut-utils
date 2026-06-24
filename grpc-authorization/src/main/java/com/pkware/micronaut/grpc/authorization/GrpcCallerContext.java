package com.pkware.micronaut.grpc.authorization;

import io.grpc.Context;
import org.jspecify.annotations.Nullable;

/**
 * gRPC {@link Context} key holding the authenticated {@link GrpcPrincipal}.
 *
 * <p>{@link GrpcAuthorizationInterceptor} stores the principal here after a successful check.
 * Service implementations read it via {@link #current()}.
 */
public final class GrpcCallerContext {

  /** The authenticated caller, or {@code null} on an anonymous-allowlisted method. */
  public static final Context.Key<GrpcPrincipal> PRINCIPAL = Context.key("grpc.authorization.principal");

  private GrpcCallerContext() {}

  /** The current caller, or {@code null} if none is attached to the active gRPC context. */
  public static @Nullable GrpcPrincipal current() {
    return PRINCIPAL.get();
  }
}
