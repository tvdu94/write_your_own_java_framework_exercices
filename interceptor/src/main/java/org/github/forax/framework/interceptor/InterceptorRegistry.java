package org.github.forax.framework.interceptor;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;


public final class InterceptorRegistry {
  private AroundAdvice advice;

  public void addAroundAdvice(Class annotationClass, AroundAdvice aroundAdvice) {
    Objects.requireNonNull(annotationClass);
    Objects.requireNonNull(aroundAdvice);
    this.advice = aroundAdvice;
  }

  public <T> T createProxy(Class<T> type, T delegate){
    Objects.requireNonNull(type);
    Objects.requireNonNull(delegate);
    return type.cast(Proxy.newProxyInstance(type.getClassLoader()
      , new Class<?>[]{type}
      , (proxy, method,args) -> {
          advice.before(delegate,method,args);
          Object result = null;
          try{
            result = Utils.invokeMethod(delegate,method,args);
          }
          finally {
            advice.after(delegate,method,args,result);

          }
          return result;
        }));
  }



}
