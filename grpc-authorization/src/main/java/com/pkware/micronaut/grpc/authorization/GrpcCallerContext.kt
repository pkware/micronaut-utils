@file:JvmName("GrpcCallerContext")

package com.pkware.micronaut.grpc.authorization

import io.grpc.Context

/** gRPC [Context] key holding the authenticated caller, or `null` on an anonymous-allowlisted method.
 *
 * [GrpcAuthorizationInterceptor] stores the principal here after a successful check.
 * Service implementations read it via [current].
 * */
public val PRINCIPAL: Context.Key<GrpcPrincipal> = Context.key("grpc.authorization.principal")

/** The current [PRINCIPAL], or `null` if none is attached to the active gRPC context.  */
public fun current(): GrpcPrincipal? = PRINCIPAL.get()
