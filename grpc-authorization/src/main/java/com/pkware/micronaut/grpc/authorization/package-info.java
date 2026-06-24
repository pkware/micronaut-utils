/**
 * Generic gRPC authorization driven by trusted request headers and {@code @RolesAllowed}
 * scope annotations, with no Micronaut Security dependency.
 *
 * <p>An application supplies a {@link com.pkware.micronaut.grpc.authorization.GrpcPrincipalResolver}
 * that builds a {@link com.pkware.micronaut.grpc.authorization.GrpcPrincipal} from gRPC metadata.
 * {@link com.pkware.micronaut.grpc.authorization.GrpcAuthorizationInterceptor} enforces the
 * per-method required scopes discovered by
 * {@link com.pkware.micronaut.grpc.authorization.GrpcScopeRegistry}, deny-by-default, and publishes
 * the principal via {@link com.pkware.micronaut.grpc.authorization.GrpcCallerContext}.
 */
@NullMarked
package com.pkware.micronaut.grpc.authorization;

import org.jspecify.annotations.NullMarked;
