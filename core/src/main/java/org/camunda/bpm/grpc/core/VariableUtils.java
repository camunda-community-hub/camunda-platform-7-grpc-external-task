/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.grpc.core;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.type.ValueType;
import org.camunda.bpm.engine.variable.value.FileValue;
import org.camunda.bpm.engine.variable.value.SerializableValue;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.camunda.bpm.grpc.ListValue;
import org.camunda.bpm.grpc.MapValue;
import org.camunda.bpm.grpc.TypedValueFieldDto;
import org.camunda.bpm.grpc.TypedValueFieldDto.Builder;

import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Empty;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.Message;
import com.google.protobuf.StringValue;
import com.google.protobuf.Timestamp;

public class VariableUtils {

  public static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

  // PACK OBJECT TO PROTO

  private static final Map<Class<?>, Function<Object, Message>> PRIMITIVES;

  static {
    Map<Class<?>, Function<Object, Message>> tempMap = new HashMap<>();
    tempMap.put(String.class, obj -> StringValue.of((String) obj));
    tempMap.put(byte[].class, obj -> BytesValue.of(ByteString.copyFrom((byte[]) obj)));
    tempMap.put(Integer.class, obj -> Int32Value.of((Integer) obj));
    tempMap.put(int.class, obj -> Int32Value.of((int) obj));
    tempMap.put(Short.class, obj -> Int32Value.of((Short) obj));
    tempMap.put(short.class, obj -> Int32Value.of((short) obj));
    tempMap.put(Double.class, obj -> DoubleValue.of((Double) obj));
    tempMap.put(double.class, obj -> DoubleValue.of((double) obj));
    tempMap.put(Float.class, obj -> FloatValue.of((Float) obj));
    tempMap.put(float.class, obj -> FloatValue.of((float) obj));
    tempMap.put(Long.class, obj -> Int64Value.of((Long) obj));
    tempMap.put(long.class, obj -> Int64Value.of((long) obj));
    tempMap.put(Boolean.class, obj -> BoolValue.of((Boolean) obj));
    tempMap.put(boolean.class, obj -> BoolValue.of((boolean) obj));
    tempMap.put(Date.class, obj -> StringValue.of(DATETIME_FORMATTER.format(
        ZonedDateTime.ofInstant(((Date) obj).toInstant(), ZoneId.systemDefault()))));
    PRIMITIVES = Collections.unmodifiableMap(tempMap);
  }

  public static Map<String, TypedValueFieldDto> toTypedValueFields(VariableMap variables) {
    return variables.keySet().stream().collect(Collectors.toMap(k -> k, k -> fromTypedValue(variables.getValueTyped(k))));
  }

  protected static TypedValueFieldDto fromTypedValue(TypedValue typedValue) {
    Builder dto = TypedValueFieldDto.newBuilder();
    ValueType type = typedValue.getType();
    if (type != null) {
      String typeName = type.getName();
      dto.setType(typeName);
      dto.putAllValueInfo(type.getValueInfo(typedValue).entrySet().stream().collect(Collectors.toMap(Entry::getKey, e -> pack(e.getValue()))));
    }
    if (typedValue instanceof SerializableValue) {
      // always send serialized
      dto.setValue(pack(((SerializableValue) typedValue).getValueSerialized()));
    } else if (typedValue instanceof FileValue) {
      // do not set the value for FileValues since we don't want to send
      // megabytes over the network without explicit request
    } else {
      dto.setValue(pack(typedValue.getValue()));
    }
    return dto.build();
  }

  public static Any pack(Object obj) {
    if (obj == null) {
      Empty data = Empty.newBuilder().build();
      return Any.pack(data);
    }
    if (PRIMITIVES.containsKey(obj.getClass())) {
      return Any.pack(PRIMITIVES.get(obj.getClass()).apply(obj));
    }
    if (obj instanceof Map) {
      return Any.pack(createMapValue((Map<?, ?>) obj));
    }
    if (obj instanceof List) {
      return Any.pack(packList((List<?>) obj));
    }
    throw new IllegalArgumentException("Cannot transform value to proto object:" + obj);
  }

  public static Message packList(List<?> objList) {
    ListValue.Builder builder = ListValue.newBuilder();
    for (Object obj : objList) {
      builder.addValues(pack(obj));
    }
    return builder.build();
  }

  public static Message createMapValue(Map<?, ?> objMap) {
    return MapValue.newBuilder().putAllValues(packMap(objMap)).build();
  }

  public static Map<String, Any> packMap(Map<?, ?> objMap) {
    return objMap.entrySet().stream().collect(Collectors.toMap(e -> String.valueOf(e.getKey()), e -> VariableUtils.pack(e.getValue())));
  }

  public static Timestamp getTimestamp(Date date) {
    return date == null ? null : Timestamp.newBuilder()
        .setSeconds(date.getTime() / 1000)
        .setNanos((int) ((date.getTime() % 1000) * 1000000))
        .build();
  }

  // UNPACK PROTO TO OBJECT

  public static Object unpack(Any any) {
    if (any.is(Empty.class)) {
      return null;
    }
    try {
      if (any.is(Int32Value.class)) {
        return any.unpack(Int32Value.class).getValue();
      }
      if (any.is(Int64Value.class)) {
        return any.unpack(Int64Value.class).getValue();
      }
      if (any.is(DoubleValue.class)) {
        return any.unpack(DoubleValue.class).getValue();
      }
      if (any.is(FloatValue.class)) {
        return any.unpack(FloatValue.class).getValue();
      }
      if (any.is(BoolValue.class)) {
        return any.unpack(BoolValue.class).getValue();
      }
      if (any.is(StringValue.class)) {
        return any.unpack(StringValue.class).getValue();
      }
      if (any.is(BytesValue.class)) {
        return any.unpack(BytesValue.class).getValue().toByteArray();
      }
      if (any.is(MapValue.class)) {
        return unpackMap(any.unpack(MapValue.class).getValuesMap());
      }
      if (any.is(ListValue.class)) {
        return unpackList(any.unpack(ListValue.class).getValuesList());
      }
    } catch (Exception e) {
      throw new IllegalArgumentException("Cannot transform proto value to object:" + any, e);
    }
    throw new IllegalArgumentException("Cannot transform proto value to object:" + any);
  }

  public static List<Object> unpackList(List<Any> valuesList) {
    return valuesList.stream().map(VariableUtils::unpack).collect(Collectors.toList());
  }

  public static Map<String, Object> unpackMap(Map<String, Any> valuesMap) {
    return valuesMap.entrySet().stream().collect(Collectors.toMap(Entry::getKey, e -> VariableUtils.unpack(e.getValue())));
  }

  public static Date getDate(Timestamp ts) {
    return Date.from(Instant
      .ofEpochSecond(ts.getSeconds() , ts.getNanos())
      .atZone(ZoneId.systemDefault())
      .toInstant());
  }

  // MISC

  public static boolean notEmpty(String value) {
    return value != null && !value.isEmpty();
  }

  public static boolean notEmpty(Collection<?> list) {
    return list != null && !list.isEmpty();
  }

  public static int getSafe(Integer value) {
    return Optional.ofNullable(value).orElse(0);
  }

  public static String getSafe(String value) {
    return Optional.ofNullable(value).orElse("");
  }

}