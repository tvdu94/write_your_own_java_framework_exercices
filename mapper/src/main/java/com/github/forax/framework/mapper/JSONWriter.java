package com.github.forax.framework.mapper;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;

public final class JSONWriter {
  public String toJSON(Object o){
    return switch (o){
      case null ->"null";
      case String str -> '"'+str+'"';
      case Boolean b -> ""+b;
      case Integer integer -> ""+integer;
      case Double d -> ""+d;
      case Object obj -> {
        var fun = map.get(obj.getClass());
        if (fun != null){
          yield fun.apply(obj);
        }


        var properties = PROPERTIES_CLASS_VALUES.get(o.getClass());
        yield properties.stream()
                .map(property -> property.generate(this,obj))
                .collect(joining(", ","{","}"));
      }
    };
  }
  private static Object extractValue(PropertyDescriptor propertyDescriptor, Object obj){
    var getter = propertyDescriptor.getReadMethod();
    return Utils.invokeMethod(obj,getter);
  }

  private interface Generator{
    String generate(JSONWriter jsonWriter, Object bean);
  }

  /*Cela permet de ne pas recharger le beanInfo a chaque fois*/
  private static final ClassValue<List<Generator>> PROPERTIES_CLASS_VALUES = new ClassValue<List<Generator>>() {
    @Override
    protected List<Generator> computeValue(Class<?> type) {
      List<PropertyDescriptor> properties;
      if(type.isRecord()){
         properties = recordProperties(type);
      }
      else{
        properties = beanProperties(type);
      }
      return properties.stream()
              .filter(propertyDescriptor -> !propertyDescriptor.getName().equals("class"))
              .<Generator>map(property ->{
                var getter = property.getReadMethod();
                var annotation = getter.getAnnotation(JSONProperty.class);
                String keyName;
                if(annotation != null){
                  keyName = annotation.value();
                }
                else{
                  keyName = property.getName();
                }
                var key = "\"" + keyName + "\": ";
                return  (writer, bean) -> key +writer.toJSON(Utils.invokeMethod(bean,getter));
              })
              .toList();

    }
  };

  private final HashMap<Class<?>,Function<Object,String>> map = new HashMap<>();

  public <T> void configure(Class<T> type, Function<T,String> fun){
    Objects.requireNonNull(type);
    Objects.requireNonNull(fun);

    var result = map.putIfAbsent(type,o -> fun.apply(type.cast(o))); //todo use compose or andThen
    if (result != null){
      throw new IllegalStateException("configuration for "+"");
    }
  }

  private static List<PropertyDescriptor> beanProperties(Class<?> type) {
    return  Arrays.stream(Utils.beanInfo(type).getPropertyDescriptors()).toList();
  }


  private static List<PropertyDescriptor> recordProperties(Class<?> type){
    var recordInfo = type.getRecordComponents();
    return Arrays.stream(recordInfo)
            .map(info -> {
              try {
                return new PropertyDescriptor(info.getName(), info.getAccessor(), null);
              } catch (IntrospectionException e) {
                throw new AssertionError(e);
              }
            })
            .toList();
  }






  }
