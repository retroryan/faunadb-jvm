package com.faunadb.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.faunadb.client.types.Codec;
import com.faunadb.client.types.Field;
import com.faunadb.client.types.Result;
import com.faunadb.client.types.Value;
import com.faunadb.client.types.Value.NullV;
import com.faunadb.client.types.Value.ObjectV;
import com.google.common.collect.ImmutableMap;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Test;

import static com.faunadb.client.types.Codec.OBJECT;
import static com.faunadb.client.types.Codec.STRING;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class ObjectCodecSpec {

  private final Codec<CustomObject> CUSTOM_OBJECT = new Codec<CustomObject>() {
    final Field<String> name = Field.at("data", "name").as(STRING);
    final Field<Long> age = Field.at("data", "age").as(LONG);

    @Override
    public Result<CustomObject> apply(Value value) {
      CustomObject res = new CustomObject();
      res.name = value.get(name);
      res.age = value.get(age).intValue();
      return Result.success(res);
    }
  };

  class CustomObject {
    String name;
    Integer age;
  }

  class ValueObject {
    @JsonProperty String something;
    @JsonProperty String another;
  }

  class NestedValueObject {
    @JsonProperty ImmutableMap<String, String> aMap;
    @JsonProperty ValueObject nested;
  }

  private ObjectMapper json;

  @Before
  public void setUp() throws Exception {
    json = new ObjectMapper().registerModule(new GuavaModule());
  }

  @Test
  public void shouldConvertASingleMap() {
    ObjectV obj = json.convertValue(ImmutableMap.of("test", "value"), ObjectV.class);
    assertThat(obj.at("test").as(STRING).get(), equalTo("value"));

    JsonNode node = this.json.valueToTree(obj);
    assertThat(node.get("test").asText(), equalTo("value"));
  }

  @Test
  public void shouldConvertObjectWithNull() {
    ValueObject value = new ValueObject();
    value.something = "huh";
    value.another = null;

    ObjectV obj = json.convertValue(value, ObjectV.class);
    assertThat(obj.at("something").as(STRING).get(), equalTo("huh"));
    assertThat(obj.at("another"), CoreMatchers.<Value>is(NullV.NULL));

    JsonNode node = json.valueToTree(obj);
    assertThat(node.get("something").asText(), equalTo("huh"));
    assertThat(node.has("another"), is(true));
    assertThat(node.get("another").isNull(), is(true));
  }

  @Test
  public void shouldConvertNestedObject() {
    NestedValueObject nested = new NestedValueObject();
    nested.aMap = ImmutableMap.of("some", "value");
    nested.nested = new ValueObject();
    nested.nested.something = "huh";
    nested.nested.another = null;

    ObjectV obj = json.convertValue(nested, ObjectV.class);
    assertThat(obj.at("aMap").as(OBJECT).get().get("some").as(STRING).get(), equalTo("value"));
    assertThat(obj.at("nested").as(OBJECT).get().get("something").as(STRING).get(), equalTo("huh"));
    assertThat(obj.at("nested").as(OBJECT).get().get("another"), CoreMatchers.<Value>is(NullV.NULL));

    JsonNode node = json.valueToTree(obj);
    assertThat(node.get("aMap").isObject(), is(true));
    assertThat(node.get("aMap").get("some").asText(), equalTo("value"));
    assertThat(node.get("nested").get("something").asText(), equalTo("huh"));
    assertThat(node.get("nested").has("another"), is(true));
    assertThat(node.get("nested").get("another").isNull(), is(true));
  }

  @Test
  public void shouldBeAbleToUseCustomCodec() throws Exception {
    ObjectV obj = new ObjectV(ImmutableMap.of(
      "data", new ObjectV(ImmutableMap.of(
        "name", new Value.StringV("Jhon"),
        "age", new Value.LongV(42)
      ))
    ));

    CustomObject custom = obj.as(CUSTOM_OBJECT).get();
    assertThat(custom.name, equalTo("Jhon"));
    assertThat(custom.age, equalTo(42));
  }
}
