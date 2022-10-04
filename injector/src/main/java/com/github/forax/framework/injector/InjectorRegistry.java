package com.github.forax.framework.injector;

import java.beans.PropertyDescriptor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;


public final class InjectorRegistry {
    private final Map<Class,Supplier> mapOfInjector = new HashMap<>();

    public <T> void registerInstance(Class<T> type, T o){
        Objects.requireNonNull(type);
        Objects.requireNonNull(o);

        if (mapOfInjector.putIfAbsent(type,() -> o) != null){
            throw new IllegalStateException("Already an instance for this type");
        }
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


    public List<PropertyDescriptor> findInjectableProperties(Class type){

    }

}
