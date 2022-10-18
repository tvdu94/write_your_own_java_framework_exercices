package com.github.forax.framework.injector;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Supplier;


public final class InjectorRegistry {
    private final Map<Class,Supplier> mapOfInjector = new HashMap<>();

    public <T> void registerInstance(Class<T> type, T o){
        Objects.requireNonNull(type);
        Objects.requireNonNull(o);

        registerProvider(type,() -> o);
    }


    public <T> T lookupInstance(Class<T> type){
        Objects.requireNonNull(type);
        var test = mapOfInjector.get(type);
        if (test == null){
            throw new IllegalStateException();
        }
        T obj = type.cast(test.get());
        if (obj == null){
            throw new NullPointerException("No instance registered for this type");
        }
        return obj;
    }

    public <T> void registerProvider(Class<T> type, Supplier<T> supplier){
        Objects.requireNonNull(type);
        Objects.requireNonNull(supplier);

        if (mapOfInjector.putIfAbsent(type,supplier) != null){
            throw new IllegalArgumentException("Already an supplier for this type");
        }
    }

    //package private for testing
    static List<PropertyDescriptor> findInjectableProperties(Class type){
        Objects.requireNonNull(type);
        return Arrays.stream(Utils.beanInfo(type).getPropertyDescriptors())
                .filter(p-> {
                    var write = p.getWriteMethod();
                    return write != null && write.isAnnotationPresent(Inject.class);
                })
                .toList();
    }


    public <T> void registerProviderClass(Class<T>  type,Class<? extends  T> providerClass){
        Objects.requireNonNull(type);
        Objects.requireNonNull(providerClass);

        var constructor = Utils.defaultConstructor(providerClass);


        var properties = findInjectableProperties(providerClass);

        registerProvider(type, () -> {
            var newProv = Utils.newInstance(constructor);

            properties.stream().map(PropertyDescriptor::getWriteMethod)
                    .forEach(method -> Utils.invokeMethod(newProv,method,lookupInstance(method.getParameterTypes()[0])));


            return newProv;
        });
    }

}
