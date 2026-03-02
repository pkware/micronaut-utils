package com.pkware.micronaut.security.grpc;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.HttpRequest;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.RouteInfo;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Minimal {@link MethodBasedRouteMatch} adapter that wraps a gRPC service's
 * {@link ExecutableMethod} so that {@code SecuredAnnotationRule} can read its
 * {@code @Secured} annotation.
 *
 * <p>{@code SecuredAnnotationRule} retrieves the route match from
 * {@link RouteAttributes#setRouteMatch}, checks {@code instanceof MethodBasedRouteMatch},
 * then calls {@link #getAnnotation} and {@link #getValue} — both of which
 * delegate to {@link #getAnnotationMetadata()}.
 *
 * <p>Routing, execution, and fulfillment methods are never called during
 * security evaluation and throw {@link UnsupportedOperationException}.
 */
final class GrpcMethodRouteMatch implements MethodBasedRouteMatch<Object, Object> {

  private final ExecutableMethod<?, ?> executableMethod;

  GrpcMethodRouteMatch(ExecutableMethod<?, ?> executableMethod) {
    this.executableMethod = executableMethod;
  }

  @Override
  public AnnotationMetadata getAnnotationMetadata() {
    return executableMethod.getAnnotationMetadata();
  }

  @SuppressWarnings("unchecked")
  @Override
  public ExecutableMethod<Object, Object> getExecutableMethod() {
    return (ExecutableMethod<Object, Object>) executableMethod;
  }

  @Override
  public @Nullable Object getTarget() {
    return null;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Class<Object> getDeclaringType() {
    return (Class<Object>) executableMethod.getDeclaringType();
  }

  @Override
  public Argument[] getArguments() {
    return executableMethod.getArguments();
  }

  @Override
  public String getMethodName() {
    return executableMethod.getMethodName();
  }

  @Override
  public Method getTargetMethod() {
    return executableMethod.getTargetMethod();
  }

  @SuppressWarnings("unchecked")
  @Override
  public ReturnType<Object> getReturnType() {
    return (ReturnType<Object>) executableMethod.getReturnType();
  }

  @Override
  public RouteInfo<Object> getRouteInfo() {
    throw new UnsupportedOperationException("gRPC methods have no HTTP route info");
  }

  @Override
  public Map<String, Object> getVariableValues() {
    return Map.of();
  }

  @Override
  public Object execute() {
    throw new UnsupportedOperationException("gRPC methods are not executed via RouteMatch");
  }

  @Override
  public Object invoke(Object... arguments) {
    throw new UnsupportedOperationException("gRPC methods are not invoked via RouteMatch");
  }

  @Override
  public Object call() {
    throw new UnsupportedOperationException("gRPC methods are not called via RouteMatch");
  }

  @SuppressWarnings("removal")
  @Override
  public void fulfill(Map<String, Object> argumentValues) {
    throw new UnsupportedOperationException("gRPC methods are not fulfilled via RouteMatch");
  }

  @Override
  public void fulfillBeforeFilters(RequestBinderRegistry registry, HttpRequest<?> request) {
    throw new UnsupportedOperationException("gRPC methods are not fulfilled via RouteMatch");
  }

  @Override
  public void fulfillAfterFilters(RequestBinderRegistry registry, HttpRequest<?> request) {
    throw new UnsupportedOperationException("gRPC methods are not fulfilled via RouteMatch");
  }

  @Override
  public boolean isFulfilled() {
    return true;
  }

  @Override
  public boolean isSatisfied(String name) {
    return true;
  }

  @Override
  public Optional<Argument<?>> getRequiredInput(String name) {
    return Optional.empty();
  }

  @Override
  public Collection<Argument<?>> getRequiredArguments() {
    return List.of();
  }
}
