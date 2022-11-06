package com.github.forax.framework.orm;

import javax.sql.DataSource;
import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.Serial;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ORM {
  private ORM() {
    throw new AssertionError();
  }



  @FunctionalInterface
  public interface TransactionBlock {
    void run() throws SQLException;
  }

  private static final Map<Class<?>, String> TYPE_MAPPING = Map.of(
      int.class, "INTEGER",
      Integer.class, "INTEGER",
      long.class, "BIGINT",
      Long.class, "BIGINT",
      String.class, "VARCHAR(255)"
  );

  private static Class<?> findBeanTypeFromRepository(Class<?> repositoryType) {
    var repositorySupertype = Arrays.stream(repositoryType.getGenericInterfaces())
        .flatMap(superInterface -> {
          if (superInterface instanceof ParameterizedType parameterizedType
              && parameterizedType.getRawType() == Repository.class) {
            return Stream.of(parameterizedType);
          }
          return null;
        })
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("invalid repository interface " + repositoryType.getName()));
    var typeArgument = repositorySupertype.getActualTypeArguments()[0];
    if (typeArgument instanceof Class<?> beanType) {
      return beanType;
    }
    throw new IllegalArgumentException("invalid type argument " + typeArgument + " for repository interface " + repositoryType.getName());
  }

  private static class UncheckedSQLException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 42L;

    private UncheckedSQLException(SQLException cause) {
      super(cause);
    }

    @Override
    public SQLException getCause() {
      return (SQLException) super.getCause();
    }
  }


  // --- do not change the code above

  private static final ThreadLocal<Connection> CONNECTION_THREAD_LOCAL = new ThreadLocal<>();

  public static void transaction(DataSource source,TransactionBlock lambda) throws SQLException{
    Objects.requireNonNull(source);
    Objects.requireNonNull(lambda);
    try(var connexion = source.getConnection()) {
      CONNECTION_THREAD_LOCAL.set(connexion);
      connexion.setAutoCommit(false);

      try {
        lambda.run();
        connexion.commit();
      } catch (SQLException | RuntimeException exception) {
        var cause = (exception instanceof UncheckedSQLException unchecked)? unchecked.getCause(): exception;
        connexion.rollback();
        throw Utils.rethrow(cause);
      } finally {
        CONNECTION_THREAD_LOCAL.remove();
      }

    }

  }

  static Connection currentConnection() throws IllegalStateException {
    var current = CONNECTION_THREAD_LOCAL.get();
    if (current == null){
      throw new IllegalStateException();
    }
    return current;
  }
  static String findTableName(Class<?> beanClass){
    var table = beanClass.getAnnotation(Table.class);
    if (table == null){
      return beanClass.getSimpleName().toUpperCase(Locale.ROOT);
    }
   return  table.value();
  }

  static String findColumnName(PropertyDescriptor property){
    var column = property.getReadMethod().getAnnotation(Column.class);
    if (column == null){
      return property.getName().toUpperCase(Locale.ROOT);
    }
    return  column.value().toUpperCase(Locale.ROOT);
  }

  static String findColumnType(PropertyDescriptor property){
    var type =property.getPropertyType();
    var mapping = TYPE_MAPPING.get(type);
    if (mapping == null){
      throw new IllegalStateException();
    };
   var nullalble  = type.isPrimitive() ? " NOT NULL" :"";

   var generatedValeu  = property.getReadMethod().isAnnotationPresent(GeneratedValue.class);
   var incre = generatedValeu ? " AUTO_INCREMENT" : "";

   return 	mapping+nullalble + incre;
  }

  static boolean isId(PropertyDescriptor property){
   return property.getReadMethod().isAnnotationPresent(Id.class);
  }

  public static void createTable(Class beanclass) throws SQLException {
    Objects.requireNonNull(beanclass);
    Connection connection = currentConnection();
    var tableName = findTableName(beanclass);
    var beanInfo = Utils.beanInfo(beanclass);

    var joiner = new StringJoiner(",\n");
    var id = (String) null;
    for (var prop : beanInfo.getPropertyDescriptors()){
      if (prop.getName().equals("class")){
        continue;
      }


      var columnName = findColumnName(prop);
      if (isId(prop)){
        if (id!= null){
          throw new IllegalStateException();
        }
        id = columnName;
      }

      var line = columnName + " " + findColumnType(prop);
      joiner.add(line);
    }

    if (id != null){
      joiner.add("PRIMARY KEY ("+id+")");
    }

    var query = "CREATE TABLE " + tableName + "(\n" + joiner + ");";

    try(Statement statement = connection.createStatement()) {
      statement.executeUpdate(query);
    } catch (SQLException e) {
      throw e;
    }
  }



  @SuppressWarnings("unchecked")
  public static <T> T createRepository(Class<T> type) {
    Objects.requireNonNull(type);
    var beanType = findBeanTypeFromRepository(type);
    var tableName = findTableName(beanType);


    InvocationHandler invocationHandler = (proxy,method,args) -> {
      var connection = currentConnection();
      try{
        if (method.getName().equals("findAll")) {
          var query = "SELECT * FROM " + tableName;
          return findAll(connection, query, Utils.beanInfo(beanType), Utils.defaultConstructor(beanType));
        }
        else if(method.getName().equals("equals") || method.getName().equals("hashCode") || method.getName().equals("toString")){
          throw new UnsupportedOperationException();
        }else {
          throw new IllegalStateException();
        }
      }
      catch (SQLException e){
        throw new UncheckedSQLException(e);
      }
    };

    return (T) Proxy.newProxyInstance(type.getClassLoader(),
    new Class<?>[] { type },
    invocationHandler);
  }



  public   static Object toEntityClass(ResultSet resultSet, BeanInfo beanInfo, Constructor<?> constructor) throws SQLException {
    var instance = Utils.newInstance(constructor);
    var properties = beanInfo.getPropertyDescriptors();
    for (var property : properties) {
      var name = property.getName();
      if (name.equals("class")) {
        continue;
      }
      var value = resultSet.getObject(name);
      Utils.invokeMethod(instance, property.getWriteMethod(), value);
    }
    return instance;
  }



  public static List<Object> findAll(Connection connection, String s, BeanInfo beanInfo, Constructor<?> constructor) throws SQLException {
    List<Object> liste = new ArrayList<>();
    try(PreparedStatement statement = connection.prepareStatement(s)){
      try (ResultSet resultSet = statement.executeQuery()){
        while(resultSet.next()) {
          liste.add(toEntityClass(resultSet,beanInfo,constructor));
        }
      }
      catch (SQLException | RuntimeException exception) {

        connection.rollback();
        throw exception;
      }
    }
    connection.commit();
    return liste;
  }





















}
