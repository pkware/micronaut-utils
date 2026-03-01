/**
 * Compile-time annotation mapping for gRPC scope authorization.
 *
 * <p>Maps {@code @Secured} → {@code @Executable} so that Micronaut generates
 * {@link io.micronaut.inject.ExecutableMethod} metadata for gRPC service methods.
 *
 * @see com.pkware.micronaut.security.grpc.SecuredAnnotationMapper
 */
@NullMarked
package com.pkware.micronaut.security.grpc;

import org.jspecify.annotations.NullMarked;
