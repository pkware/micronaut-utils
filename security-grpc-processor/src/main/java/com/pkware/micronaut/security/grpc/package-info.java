/**
 * Compile-time annotation mapping for gRPC scope authorization.
 *
 * <p>Ships two {@code NamedAnnotationMapper}s, both mapping their annotation to {@code @Executable}
 * so that Micronaut generates {@link io.micronaut.inject.ExecutableMethod} metadata for gRPC service
 * methods:
 * <ul>
 *   <li>{@link com.pkware.micronaut.security.grpc.SecuredAnnotationMapper} maps {@code @Secured},
 *       serving the {@code security-grpc} module.</li>
 *   <li>{@link com.pkware.micronaut.security.grpc.RolesAllowedAnnotationMapper} maps
 *       {@code jakarta.annotation.security.RolesAllowed}, serving the {@code grpc-authorization}
 *       module.</li>
 * </ul>
 *
 * @see com.pkware.micronaut.security.grpc.SecuredAnnotationMapper
 * @see com.pkware.micronaut.security.grpc.RolesAllowedAnnotationMapper
 */
@NullMarked
package com.pkware.micronaut.security.grpc;

import org.jspecify.annotations.NullMarked;
