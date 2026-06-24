package com.pkware.micronaut.grpc.authorization;

import java.util.Set;

/**
 * The authenticated caller's identity, as enforced by {@link GrpcAuthorizationInterceptor}.
 *
 * <p>JavaBean getters. A Kotlin implementer cannot satisfy {@code getSubject()} with an
 * {@code override val subject} property (Kotlin rejects overriding a Java getter with a property —
 * "overrides nothing"); declare explicit {@code override fun getSubject()} / {@code override fun getScopes()},
 * keeping any data-class property getter renamed via {@code @get:JvmName} to avoid a JVM signature clash.
 */
public interface GrpcPrincipal {

  /** The authenticated subject (a user id or a service/client id; the module does not branch). */
  String getSubject();

  /** The scopes granted to this caller. Empty if the caller has none. */
  Set<String> getScopes();
}
