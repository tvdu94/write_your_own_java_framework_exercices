package com.github.forax.framework.orm;

import javax.sql.DataSource;
import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.Serial;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.sql.Connection;
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
        connexion.rollback();
        throw exception;
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

























}
