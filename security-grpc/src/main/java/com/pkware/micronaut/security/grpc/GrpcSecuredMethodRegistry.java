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
import java.util.List;
import java.util.Map;

/**
 * Builds a {@code fullMethodName → requiredRoles} map at startup from {@code @Secured}
 * annotations on gRPC service methods.
 *
 * <p>For each {@link BindableService}, inspects its gRPC {@link ServerServiceDefinition}
 * to discover method names, then looks up the corresponding
 * {@link ExecutableMethod} to read {@code @Secured} role values.
 *
 * <p>Methods that are not overridden or not annotated with {@code @Secured} are absent
 * from the map — callers should treat absence as "deny" for a deny-by-default model.
 */
@Singleton
public final class GrpcSecuredMethodRegistry {

  private static final String SECURED = "io.micronaut.security.annotation.Secured";

  private final Map<String, List<String>> methodRoles;

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
    this.methodRoles = buildMethodRolesMap(services, beanContext);
  }

  /**
   * Returns the roles required for the given gRPC full method name, or {@code null}
   * if the method is not registered.
   *
   * <p>A {@code null} return means the method was not annotated with {@code @Secured}
   * and should be denied in a deny-by-default model.
   *
   * @param fullMethodName the gRPC full method name (e.g. {@code "recurse.site.TaskQueueService/GetTaskQueues"}).
   * @return required roles, or {@code null} if not registered.
   */
  public @Nullable List<String> getRequiredRoles(String fullMethodName) {
    return methodRoles.get(fullMethodName);
  }

  private static Map<String, List<String>> buildMethodRolesMap(
      Collection<BindableService> services,
      BeanContext beanContext) {
    var map = new HashMap<String, List<String>>();

    for (var service : services) {
      BeanDefinition<?> beanDefinition = beanContext.getBeanDefinition(service.getClass());
      ServerServiceDefinition serviceDefinition = service.bindService();
      String serviceName = serviceDefinition.getServiceDescriptor().getName();

      for (var grpcMethod : serviceDefinition.getServiceDescriptor().getMethods()) {
        String methodName = extractMethodName(grpcMethod);

        for (var executableMethod : beanDefinition.getExecutableMethods()) {
          if (executableMethod.getMethodName().equals(methodName)) {
            String[] roles = executableMethod.stringValues(SECURED);
            if (roles.length > 0) {
              String fullMethodName = MethodDescriptor.generateFullMethodName(serviceName, methodName);
              map.put(fullMethodName, List.of(roles));
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
