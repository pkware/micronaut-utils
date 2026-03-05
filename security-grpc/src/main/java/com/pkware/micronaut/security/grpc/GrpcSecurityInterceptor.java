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
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.filter.ServerFilterPhase;
import io.micronaut.http.simple.SimpleHttpRequest;
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
import java.util.Optional;

/**
 * gRPC {@link ServerInterceptor} that enforces access control on gRPC service methods
 * via Micronaut Security's standard {@link SecurityRule} chain.
 *
 * <p>Delegates authentication to Micronaut Security's {@link AuthenticationFetcher} chain
 * by building a synthetic {@link HttpRequest} from gRPC {@link Metadata} headers.
 *
 * <p>Authorization is delegated to Micronaut Security's {@link SecurityRule} chain —
 * the same chain used by {@code SecurityFilter} for HTTP. This means:
 * <ul>
 *   <li>{@code @Secured} annotations on gRPC service methods are enforced via
 *       {@code SecuredAnnotationRule}, using a {@link GrpcMethodRouteMatch} adapter
 *       placed on the synthetic request's {@code ROUTE_MATCH} attribute.</li>
 *   <li>Configuration-driven access rules via {@code micronaut.security.intercept-url-map}
 *       are enforced by {@code ConfigurationInterceptUrlMapRule}. Patterns are matched
 *       against the synthetic request path {@code "/" + fullMethodName} — for example,
 *       {@code /grpc.health.v1.Health/*} to allow all methods in the health service.
 *       This is particularly useful for third-party services that cannot carry
 *       {@code @Secured} annotations.</li>
 *   <li>Any custom {@code SecurityRule} beans apply equally to gRPC and HTTP.</li>
 * </ul>
 *
 * <p>Deny-by-default: if no rule returns {@link SecurityRuleResult#ALLOWED}, the call
 * is rejected. Methods that are not covered by any security rule — no {@code @Secured}
 * annotation and no matching {@code intercept-url-map} pattern — are rejected with
 * {@link Status#PERMISSION_DENIED} "Method not allowed".
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
  private static final ServerCall.Listener NO_OP_LISTENER = new ServerCall.Listener<>() {
  };

  private final Collection<AuthenticationFetcher<HttpRequest<?>>> authenticationFetchers;
  private final Collection<SecurityRule<HttpRequest<?>>> securityRules;
  private final GrpcSecuredMethodRegistry registry;
  private final MeterRegistry meterRegistry;

  /**
   * Creates the interceptor.
   *
   * @param authenticationFetchers Micronaut Security's authentication fetcher chain.
   * @param securityRules Micronaut Security's authorization rule chain.
   * @param registry annotation-driven method→{@link ExecutableMethod} map built at startup.
   * @param meterRegistry for recording auth attempt counts and authentication latency.
   */
  public GrpcSecurityInterceptor(
    Collection<AuthenticationFetcher<HttpRequest<?>>> authenticationFetchers,
    Collection<SecurityRule<HttpRequest<?>>> securityRules,
    GrpcSecuredMethodRegistry registry,
    MeterRegistry meterRegistry) {
    this.authenticationFetchers = authenticationFetchers;
    this.securityRules = securityRules;
    this.registry = registry;
    this.meterRegistry = meterRegistry;
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

    // Look up the @Secured ExecutableMethod for this gRPC method. A null result means
    // the method has no @Secured annotation and is not in the registry. We do NOT
    // short-circuit here: ConfigurationInterceptUrlMapRule (and any custom SecurityRule
    // bean) may still explicitly allow or deny the method via intercept-url-map config.
    // The deny-by-default guarantee is preserved — if all rules return UNKNOWN,
    // evaluateRules() returns Optional.empty() and we reject with "Method not allowed".
    ExecutableMethod<?, ?> executableMethod = registry.getExecutableMethod(fullMethodName);

    HttpRequest<?> syntheticRequest = toHttpRequest(fullMethodName, headers, executableMethod);
    Authentication authentication = authenticate(syntheticRequest);
    Optional<SecurityRuleResult> decision = evaluateRules(syntheticRequest, authentication);

    if (decision.orElse(SecurityRuleResult.REJECTED) == SecurityRuleResult.ALLOWED) {
      Context context = Context.current();
      if (authentication != null) {
        context = context.withValue(GrpcSecurityContext.AUTHENTICATION, authentication);
      }
      recordAttempt(GrpcSecurityMetrics.OUTCOME_SUCCESS, GrpcSecurityMetrics.REASON_NONE);
      return Contexts.interceptCall(context, call, headers, next);
    }

    // The gRPC status code distinguishes "method not exposed to the security system at all"
    // from "method is known but the caller lacks credentials or roles". An empty Optional
    // means no rule made an explicit decision — the method has no @Secured annotation
    // and no matching intercept-url-map pattern. An Optional.of(REJECTED) means a rule
    // explicitly saw the method and rejected the caller based on auth/authz criteria.
    if (decision.isEmpty()) {
      LOGGER.atWarn().log(
        "Denying gRPC method with no security configuration: {}. "
        + "Add @Secured to the method or configure intercept-url-map.",
        fullMethodName);
      call.close(Status.PERMISSION_DENIED.withDescription("Method not allowed"), headers);
      recordAttempt(GrpcSecurityMetrics.OUTCOME_FAILED, GrpcSecurityMetrics.REASON_METHOD_NOT_ALLOWED);
    } else if (authentication == null) {
      call.close(Status.UNAUTHENTICATED.withDescription("Authentication required"), headers);
      recordAttempt(GrpcSecurityMetrics.OUTCOME_FAILED, GrpcSecurityMetrics.REASON_REJECTED);
    } else {
      call.close(Status.PERMISSION_DENIED.withDescription("Insufficient scope"), headers);
      recordAttempt(GrpcSecurityMetrics.OUTCOME_FAILED, GrpcSecurityMetrics.REASON_REJECTED);
    }
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
   * {@link SecurityRuleResult#UNKNOWN}:
   * <ul>
   *   <li>{@link Optional#empty()} is returned — no rule had an opinion about this method.
   *       The caller should treat this as deny-by-default and report "Method not allowed".</li>
   *   <li>{@link Optional#of(SecurityRuleResult) Optional.of(REJECTED)} means a rule
   *       explicitly rejected the caller based on auth/authz criteria.</li>
   * </ul>
   */
  private Optional<SecurityRuleResult> evaluateRules(HttpRequest<?> request, @io.micronaut.core.annotation.Nullable Authentication authentication) {
    SecurityRuleResult result = Flux.fromIterable(securityRules)
      .concatMap(rule -> Mono.from(rule.check(request, authentication))
        .defaultIfEmpty(SecurityRuleResult.UNKNOWN)
        .filter(r -> r != SecurityRuleResult.UNKNOWN))
      .next()
      .block(AUTHENTICATION_TIMEOUT);

    // null means all rules returned UNKNOWN — no rule knew about this method.
    // Non-null means some rule made an explicit ALLOWED or REJECTED decision.
    return Optional.ofNullable(result);
  }

  /**
   * Delegates to Micronaut Security's {@link AuthenticationFetcher} chain via a
   * synthetic {@link HttpRequest} built from gRPC {@link Metadata}.
   */
  private @io.micronaut.core.annotation.Nullable Authentication authenticate(HttpRequest<?> syntheticRequest) {
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
   * Builds a synthetic {@link HttpRequest} from gRPC {@link Metadata} headers.
   *
   * <p>The request path is {@code "/" + fullMethodName}. This is the value that Micronaut
   * Security's rules pattern-match against — {@code ConfigurationInterceptUrlMapRule}
   * evaluates {@code intercept-url-map} patterns (e.g. {@code /grpc.health.v1.Health/*})
   * against this path.
   *
   * <p>gRPC over HTTP/2 transmits metadata as HTTP headers, so copying them into an
   * {@link HttpRequest} lets Micronaut Security's existing pipeline ({@code BearerTokenReader},
   * {@code TokenResolver}, etc.) work unchanged.
   *
   * <p>When an {@code executableMethod} is provided, a {@link GrpcMethodRouteMatch} is set
   * on the request so that {@code SecuredAnnotationRule} can read the {@code @Secured}
   * annotation. For methods without {@code @Secured} (not in the registry), no route match
   * is set — {@code SecuredAnnotationRule} returns {@code UNKNOWN}, leaving the decision
   * to other rules like {@code ConfigurationInterceptUrlMapRule}.
   */
  private static HttpRequest<?> toHttpRequest(
    String fullMethodName,
    Metadata headers,
    @Nullable ExecutableMethod<?, ?> executableMethod) {
    MutableHttpRequest<?> request = new SimpleHttpRequest<>(HttpMethod.GET, "/" + fullMethodName, null);
    for (String key : headers.keys()) {
      if (!key.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
        String value = headers.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER));
        if (value != null) {
          request.header(key, value);
        }
      }
    }
    if (executableMethod != null) {
      RouteAttributes.setRouteMatch(request, new GrpcMethodRouteMatch(executableMethod));
    }
    return request;
  }
}
