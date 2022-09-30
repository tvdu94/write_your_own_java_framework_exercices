package com.github.forax.framework.mapper;

import com.github.forax.framework.mapper.ToyJSONParser.JSONVisitor;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ToyJSONParserTest {
  private static Object asJava(String text) {
    var visitor = new JSONVisitor() {
      private Object result;
      private final ArrayDeque<Object> stack = new ArrayDeque<>();

      @Override
      @SuppressWarnings("unchecked")
      public void value(String key, Object value) {
        var data = stack.peek();
        if (data instanceof Map<?,?> map) {
          ((Map<String, Object>) map).put(key, value);
          return;
        }
        if (data instanceof List<?> list) {
          ((List<Object>) list).add(value);
          return;
        }
        throw new AssertionError();
      }

      @Override
      public void startObject(String key) {
        stack.push(new HashMap<String, Object>());
      }

      @Override
      public void endObject(String key) {
        var data = stack.pop();
        if (stack.isEmpty()) {
          result = data;
        } else {
          value(key, data);
        }
      }

      @Override
      public void startArray(String key) {
        stack.push(new ArrayList<>());
      }

      @Override
      public void endArray(String key) {
        var data = stack.pop();
        if (stack.isEmpty()) {
          result = data;
        } else {
          value(key, data);
        }
      }
    };
    ToyJSONParser.parse(text, visitor);
    return visitor.result;
  }

  @Test
  public void parseObjects() {
    assertAll(
        () -> assertEquals(Map.of(), asJava("{}")),
        () -> assertEquals(Map.of(), asJava("{ }")),
        () -> assertEquals(Map.of(
            "key2", false,
            "key3", true,
            "key4", 123,
            "key5", 145.4,
            "key6", "string"
        ), asJava("""
            {
              "key2": false,
              "key3": true,
              "key4": 123,
              "key5": 145.4,
              "key6": "string"
            }
            """)),
        () -> assertEquals(Map.of("foo", "bar"), asJava("""
            {
              "foo": "bar"
            }
            """)),
        () -> assertEquals(Map.of("foo", "bar", "bob-one", 42), asJava("""
            {
              "foo": "bar",
              "bob-one": 42
            }
            """))
    );
  }

  @Test
  public void parseObjectsWithNull() {
    assertEquals(new HashMap<String, Object>() {{
      put("foo", null);
    }}, asJava("""
        {
          "foo": null
        }
        """));
  }

  @Test
  public void parseArrays() {
    assertAll(
        () -> assertEquals(List.of(), asJava("[]")),
        () -> assertEquals(List.of(), asJava("[ ]")),
        () -> assertEquals(
            List.of(false,true,123,145.4,"string"),
            asJava("""
            [
              false, true, 123, 145.4, "string"
            ]
            """)),
        () -> assertEquals(List.of("foo", "bar"), asJava("""
            [
              "foo",
              "bar"
            ]
            """)),
        () -> assertEquals(List.of("foo", "bar", "bob-one", 42), asJava("""
            [ "foo", "bar", "bob-one", 42 ]\
            """))
    );
  }

  @Test
  public void parseArraysWithNull() {
    assertEquals(Arrays.asList(13.4, null), asJava("""
        [ 13.4, null ]
        """));
  }
}