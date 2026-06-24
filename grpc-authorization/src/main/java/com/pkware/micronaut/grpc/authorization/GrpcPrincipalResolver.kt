package com.pkware.micronaut.grpc.authorization;

import io.grpc.Metadata;
import org.jspecify.annotations.Nullable;

/**
 * Builds a {@link GrpcPrincipal} from trusted gRPC request metadata. Synchronous; performs no I/O.
 *
 * <p>The application provides the implementation — this module ships none. Returning {@code null}
 * means the request carries no identity (unauthenticated).
 */
public interface GrpcPrincipalResolver {

  /** Returns the caller for these headers, or {@code null} if the request is unauthenticated. */
  @Nullable GrpcPrincipal resolve(Metadata headers);
}
