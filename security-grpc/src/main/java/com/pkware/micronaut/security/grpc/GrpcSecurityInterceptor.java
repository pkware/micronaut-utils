package com.pkware.micronaut.security.grpc;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.filter.ServerFilterPhase;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.filters.AuthenticationFetcher;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.security.rules.SecurityRuleResult;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collection;

/**
 * gRPC {@link ServerInterceptor} that enforces {@code @Secured} annotations on
 * gRPC service methods.
 *
 * <p>Delegates authentication to Micronaut Security's {@link AuthenticationFetcher} chain
 * by building a synthetic {@link HttpRequest} from gRPC {@link Metadata} headers.
 *
 * <p>Authorization is delegated to Micronaut Security's {@link SecurityRule} chain —
 * the same chain used by {@code SecurityFilter} for HTTP. This means custom
 * {@code SecurityRule} beans apply to gRPC methods the same way they do to HTTP endpoints.
 *
 * <p>Deny-by-default: methods not registered in the {@link GrpcSecuredMethodRegistry}
 * are rejected with {@link Status#PERMISSION_DENIED}. Only services listed in
 * {@link GrpcSecurityConfiguration#getUnauthenticatedServices()} bypass authentication.
 *
 * <p>After successful validation, the {@link Authentication} is stored in the gRPC
 * {@link Context} via {@link GrpcSecurityContext#AUTHENTICATION} for downstream
 * interceptors and service implementations.
 *
 * <p>Runs at {@link ServerFilterPhase#SECURITY} order, consistent with the HTTP
 * {@code SecurityFilter}.
 */
@Singleton
public final class GrpcSecurityInterceptor implements ServerInterceptor, Ordered {

  private static final Logger LOGGER = LoggerFactory.getLogger(GrpcSecurityInterceptor.class);

  private static final Duration AUTHENTICATION_TIMEOUT = Duration.ofSeconds(10);

  @SuppressWarnings("rawtypes")
  private static final ServerCall.Listener NO_OP_LISTENER = new ServerCall.Listener<>() {};

  private final Collection<AuthenticationFetcher<HttpRequest<?>>> authenticationFetchers;
  private final Collection<SecurityRule<HttpRequest<?>>> securityRules;
  private final GrpcSecuredMethodRegistry registry;
  private final MeterRegistry meterRegistry;
  private final GrpcSecurityConfiguration configuration;

  /**
   * Creates the interceptor.
   *
   * @param authenticationFetchers Micronaut Security's authentication fetcher chain.
   * @param securityRules Micronaut Security's authorization rule chain.
   * @param registry annotation-driven method→{@link ExecutableMethod} map built at startup.
   * @param meterRegistry for recording auth attempt counts and authentication latency.
   * @param configuration configurable unauthenticated services and other settings.
   */
  public GrpcSecurityInterceptor(
      Collection<AuthenticationFetcher<HttpRequest<?>>> authenticationFetchers,
      Collection<SecurityRule<HttpRequest<?>>> securityRules,
      GrpcSecuredMethodRegistry registry,
      MeterRegistry meterRegistry,
      GrpcSecurityConfiguration configuration) {
    this.authenticationFetchers = authenticationFetchers;
    this.securityRules = securityRules;
    this.registry = registry;
    this.meterRegistry = meterRegistry;
    this.configuration = configuration;
  }

  @Override
  public int getOrder() {
    return ServerFilterPhase.SECURITY.order();
  }

  @Override
  @SuppressWarnings("unchecked")
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call,
      Metadata headers,
      ServerCallHandler<ReqT, RespT> next) {

    String fullMethodName = call.getMethodDescriptor().getFullMethodName();
    String serviceName = call.getMethodDescriptor().getServiceName();

    if (serviceName != null && configuration.getUnauthenticatedServices().contains(serviceName)) {
      return next.startCall(call, headers);
    }

    ExecutableMethod<?, ?> executableMethod = registry.getExecutableMethod(fullMethodName);
    if (executableMethod == null) {
      LOGGER.atWarn().log("Denying unlisted gRPC method: {}", fullMethodName);
      call.close(Status.PERMISSION_DENIED.withDescription("Method not allowed"), headers);
      recordAttempt(GrpcSecurityMetrics.OUTCOME_FAILED, GrpcSecurityMetrics.REASON_METHOD_NOT_ALLOWED);
      return (ServerCall.Listener<ReqT>) NO_OP_LISTENER;
    }

    HttpRequest<?> syntheticRequest = toHttpRequest(fullMethodName, headers, executableMethod);
    Authentication authentication = authenticate(syntheticRequest);
    SecurityRuleResult result = evaluateRules(syntheticRequest, authentication);

    if (result == SecurityRuleResult.ALLOWED) {
      Context context = Context.current();
      if (authentication != null) {
        context = context.withValue(GrpcSecurityContext.AUTHENTICATION, authentication);
      }
      recordAttempt(GrpcSecurityMetrics.OUTCOME_SUCCESS, GrpcSecurityMetrics.REASON_NONE);
      return Contexts.interceptCall(context, call, headers, next);
    }

    if (authentication == null) {
      call.close(Status.UNAUTHENTICATED.withDescription("Authentication required"), headers);
    } else {
      call.close(Status.PERMISSION_DENIED.withDescription("Insufficient scope"), headers);
    }
    recordAttempt(GrpcSecurityMetrics.OUTCOME_FAILED, GrpcSecurityMetrics.REASON_REJECTED);
    return (ServerCall.Listener<ReqT>) NO_OP_LISTENER;
  }

  private void recordAttempt(String outcome, String reason) {
    meterRegistry.counter(
        GrpcSecurityMetrics.AUTH_ATTEMPTS,
        GrpcSecurityMetrics.TAG_OUTCOME, outcome,
        GrpcSecurityMetrics.TAG_REASON, reason
    ).increment();
  }

  /**
   * Evaluates the {@link SecurityRule} chain, mirroring {@code SecurityFilter.checkRules()}.
   *
   * <p>Rules are evaluated in order. The first rule to return {@link SecurityRuleResult#ALLOWED}
   * or {@link SecurityRuleResult#REJECTED} wins. If all rules return
   * {@link SecurityRuleResult#UNKNOWN}, the result is {@code REJECTED} (deny-by-default).
   */
  private SecurityRuleResult evaluateRules(HttpRequest<?> request, @Nullable Authentication authentication) {
    SecurityRuleResult result = Flux.fromIterable(securityRules)
        .concatMap(rule -> Mono.from(rule.check(request, authentication))
            .defaultIfEmpty(SecurityRuleResult.UNKNOWN)
            .filter(r -> r != SecurityRuleResult.UNKNOWN))
        .next()
        .block(AUTHENTICATION_TIMEOUT);

    return result != null ? result : SecurityRuleResult.REJECTED;
  }

  /**
   * Delegates to Micronaut Security's {@link AuthenticationFetcher} chain via a
   * synthetic {@link HttpRequest} built from gRPC {@link Metadata}.
   */
  private @Nullable Authentication authenticate(HttpRequest<?> syntheticRequest) {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      return Flux.fromIterable(authenticationFetchers)
          .flatMap(fetcher -> fetcher.fetchAuthentication(syntheticRequest))
          .next()
          .block(AUTHENTICATION_TIMEOUT);
    } finally {
      sample.stop(meterRegistry.timer(GrpcSecurityMetrics.AUTHENTICATION_DURATION));
    }
  }

  /**
   * Builds a synthetic {@link HttpRequest} from gRPC {@link Metadata} headers,
   * with the {@link ExecutableMethod} set as a {@link GrpcMethodRouteMatch} via
   * {@link RouteAttributes#setRouteMatch} so that {@code SecuredAnnotationRule} can
   * read the {@code @Secured} annotation.
   *
   * <p>gRPC over HTTP/2 transmits metadata as HTTP headers, so copying them into an
   * {@link HttpRequest} lets Micronaut Security's existing pipeline ({@code BearerTokenReader},
   * {@code TokenResolver}, etc.) work unchanged.
   */
  private static HttpRequest<?> toHttpRequest(
      String fullMethodName,
      Metadata headers,
      ExecutableMethod<?, ?> executableMethod) {
    MutableHttpRequest<?> request = HttpRequest.GET("/" + fullMethodName);
    for (String key : headers.keys()) {
      if (!key.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
        String value = headers.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER));
        if (value != null) {
          request.header(key, value);
        }
      }
    }
    RouteAttributes.setRouteMatch(request, new GrpcMethodRouteMatch(executableMethod));
    return request;
  }
}
