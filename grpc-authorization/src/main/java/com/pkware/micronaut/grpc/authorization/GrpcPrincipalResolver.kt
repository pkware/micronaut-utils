package com.pkware.micronaut.grpc.authorization

import io.grpc.Metadata

/**
 * Builds a [GrpcPrincipal] from trusted gRPC request metadata. Synchronous; performs no I/O.
 *
 * The application provides the implementation — this module ships none. Returning `null`
 * means the request carries no identity (unauthenticated).
 */
public interface GrpcPrincipalResolver {
  /** Returns the caller for these headers, or `null` if the request is unauthenticated.  */
  public fun resolve(headers: Metadata): GrpcPrincipal?
}
