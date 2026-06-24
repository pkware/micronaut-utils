package com.pkware.micronaut.grpc.authorization

/**
 * The authenticated caller's identity, as enforced by [GrpcAuthorizationInterceptor].
 */
public interface GrpcPrincipal {
  /** The authenticated subject (a user id or a service/client id; the module does not branch). */
  public val subject: String

  /** The scopes granted to this caller. Empty if the caller has none. */
  public val scopes: Set<String>
}
