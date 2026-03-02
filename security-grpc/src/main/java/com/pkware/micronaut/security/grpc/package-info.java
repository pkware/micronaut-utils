/**
 * Runtime gRPC security for Micronaut.
 *
 * <p>{@link com.pkware.micronaut.security.grpc.GrpcSecurityInterceptor} enforces
 * {@code @Secured} annotations on gRPC service methods — deny-by-default authorization
 * using the same security pipeline as HTTP:
 *
 * <ol>
 *   <li><strong>Authentication:</strong> Micronaut Security's
 *       {@link io.micronaut.security.filters.AuthenticationFetcher} chain, via a synthetic
 *       {@link io.micronaut.http.HttpRequest} built from gRPC {@link io.grpc.Metadata} headers.</li>
 *   <li><strong>Authorization:</strong> Micronaut Security's
 *       {@link io.micronaut.security.rules.SecurityRule} chain, including
 *       {@code SecuredAnnotationRule} and any custom rules. The gRPC method's
 *       {@code @Secured} annotation is exposed via a
 *       {@link io.micronaut.web.router.MethodBasedRouteMatch} adapter on the
 *       synthetic request's {@code ROUTE_MATCH} attribute.</li>
 * </ol>
 *
 * <p>{@link com.pkware.micronaut.security.grpc.GrpcSecuredMethodRegistry} builds the
 * {@code fullMethodName → ExecutableMethod} map at startup from {@code @Secured} annotations.
 *
 * <p>Requires the companion {@code security-grpc-processor} artifact on the annotation
 * processor classpath to generate the necessary {@code ExecutableMethod} metadata.
 *
 * @see com.pkware.micronaut.security.grpc.GrpcSecurityInterceptor
 * @see com.pkware.micronaut.security.grpc.GrpcSecuredMethodRegistry
 */
@NullMarked
package com.pkware.micronaut.security.grpc;

import org.jspecify.annotations.NullMarked;
