package com.pkware.micronaut.security.grpc;

import io.grpc.BindableService;
import io.grpc.MethodDescriptor;
import io.grpc.ServerServiceDefinition;
import io.micronaut.context.BeanContext;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds a {@code fullMethodName → ExecutableMethod} map at startup from {@code @Secured}
 * annotations on gRPC service methods.
 *
 * <p>For each {@link BindableService}, inspects its gRPC {@link ServerServiceDefinition}
 * to discover method names, then looks up the corresponding
 * {@link ExecutableMethod} to verify it carries {@code @Secured}.
 *
 * <p>Methods that are not overridden or not annotated with {@code @Secured} are absent
 * from the map — callers should treat absence as "deny" for a deny-by-default model.
 */
@Singleton
public final class GrpcSecuredMethodRegistry {

  private static final String SECURED = "io.micronaut.security.annotation.Secured";

  private final Map<String, ExecutableMethod<?, ?>> methodExecutables;

  /**
   * Creates the registry by scanning all {@link BindableService} beans for
   * {@code @Secured} annotations.
   *
   * @param services all gRPC services registered in the application context.
   * @param beanContext for looking up bean definitions and executable method metadata.
   */
  public GrpcSecuredMethodRegistry(
    Collection<BindableService> services,
    BeanContext beanContext) {
    this.methodExecutables = buildMethodExecutablesMap(services, beanContext);
  }

  /**
   * Returns the {@link ExecutableMethod} for the given gRPC full method name, or {@code null}
   * if the method is not registered.
   *
   * <p>A {@code null} return means the method was not annotated with {@code @Secured}
   * and should be denied in a deny-by-default model.
   *
   * @param fullMethodName the gRPC full method name (e.g. {@code "helloworld.Greeter/SayHello"}).
   * @return the executable method, or {@code null} if not registered.
   */
  public @Nullable ExecutableMethod<?, ?> getExecutableMethod(String fullMethodName) {
    return methodExecutables.get(fullMethodName);
  }

  private static Map<String, ExecutableMethod<?, ?>> buildMethodExecutablesMap(
    Collection<BindableService> services,
    BeanContext beanContext) {
    var map = new HashMap<String, ExecutableMethod<?, ?>>();

    for (var service : services) {
      BeanDefinition<?> beanDefinition = beanContext.getBeanDefinition(service.getClass());
      ServerServiceDefinition serviceDefinition = service.bindService();

      for (var grpcMethod : serviceDefinition.getServiceDescriptor().getMethods()) {
        String methodName = extractMethodName(grpcMethod);

        for (var executableMethod : beanDefinition.getExecutableMethods()) {
          if (executableMethod.getMethodName().equals(methodName)) {
            String[] roles = executableMethod.stringValues(SECURED);
            if (roles.length > 0) {
              map.put(grpcMethod.getFullMethodName(), executableMethod);
            }
            break;
          }
        }
      }
    }

    return map;
  }

  /**
   * Extracts the method name from a gRPC {@link MethodDescriptor}.
   *
   * <p>The full method name is formatted as {@code "service/method"} — this returns
   * the portion after the {@code /}.
   */
  private static String extractMethodName(MethodDescriptor<?, ?> grpcMethod) {
    String fullMethodName = grpcMethod.getFullMethodName();
    int slashIndex = fullMethodName.lastIndexOf('/');
    return slashIndex >= 0 ? fullMethodName.substring(slashIndex + 1) : fullMethodName;
  }
}
