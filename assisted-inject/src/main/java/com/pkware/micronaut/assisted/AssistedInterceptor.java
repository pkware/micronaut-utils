package com.pkware.micronaut.assisted;

import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanContext;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

@InterceptorBean(Assisted.class)
class AssistedInterceptor implements MethodInterceptor<Object, Object> {
  private final BeanContext context;

  AssistedInterceptor(BeanContext context) {
    this.context = Objects.requireNonNull(context);
  }

  @Nullable
  @Override
  public Object intercept(MethodInvocationContext<Object, Object> invocationContext) {
    Class<?> beanType = invocationContext.getReturnType().getType();
    return context.createBean(beanType, invocationContext.getParameterValues());
  }
}
