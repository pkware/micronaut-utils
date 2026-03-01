/**
 * Runtime gRPC scope authorization for Micronaut.
 *
 * <p>Builds a {@code fullMethodName → requiredRoles} map at startup from {@code @Secured}
 * annotations on gRPC service methods, for use by a {@link io.grpc.ServerInterceptor}.
 *
 * <p>Requires the companion {@code security-grpc-processor} artifact on the annotation
 * processor classpath to generate the necessary {@code ExecutableMethod} metadata.
 *
 * @see com.pkware.micronaut.security.grpc.GrpcSecuredMethodRegistry
 */
@NullMarked
package com.pkware.micronaut.security.grpc;

import org.jspecify.annotations.NullMarked;
