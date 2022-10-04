package com.github.forax.framework.injector;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
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
        T obj = type.cast(mapOfInjector.get(type).get());
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
                    if (write != null){
                        return write.isAnnotationPresent(Inject.class);
                    }
                    return false;
                })
                .toList();
    }

}
