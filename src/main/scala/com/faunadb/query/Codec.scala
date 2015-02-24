package com.faunadb.query

import com.fasterxml.jackson.core.{JsonParseException, JsonParser, JsonGenerator}
import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.databind.`type`.TypeFactory
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier
import java.util.HashMap
import com.fasterxml.jackson.core.JsonToken._

import com.fasterxml.jackson.databind.node.ObjectNode
import scala.collection.JavaConversions._

class FaunaDeserializerModifier extends BeanDeserializerModifier {
  val setDeserializer = new SetDeserializer
  val primitiveDeserializer = new PrimitiveDeserializer

  override def modifyDeserializer(config: DeserializationConfig, beanDesc: BeanDescription, deserializer: JsonDeserializer[_]): JsonDeserializer[_] = {
    if (beanDesc.getBeanClass == classOf[Set])
      setDeserializer
    else if (beanDesc.getBeanClass == classOf[Primitive])
      primitiveDeserializer
    else
      deserializer
  }
}

class PaginateSerializer extends JsonSerializer[Paginate] {
  override def serialize(t: Paginate, jsonGenerator: JsonGenerator, serializerProvider: SerializerProvider): Unit = {
    jsonGenerator.writeStartObject()
    jsonGenerator.writeObjectField("paginate", t.resource)

    t.ts.foreach { tsNum =>
      jsonGenerator.writeNumberField("ts", tsNum)
    }

    t.cursor.foreach {
      case b: Before => jsonGenerator.writeObjectField("before", b.ref)
      case a: After => jsonGenerator.writeObjectField("after", a.ref)
    }

    t.size.foreach { sNum =>
      jsonGenerator.writeNumberField("size", sNum)
    }

    jsonGenerator.writeEndObject()
  }
}

class EventsSerializer extends JsonSerializer[Events] {
  override def serialize(t: Events, jsonGenerator: JsonGenerator, serializerProvider: SerializerProvider): Unit = {
    jsonGenerator.writeStartObject()
    jsonGenerator.writeObjectField("events", t.resource)

    t.cursor.foreach {
      case b: Before => jsonGenerator.writeObjectField("before", b.ref)
      case a: After => jsonGenerator.writeObjectField("after", a.ref)
    }

    t.size.foreach { sNum =>
      jsonGenerator.writeNumberField("size", sNum)
    }

    jsonGenerator.writeEndObject()
  }
}

class PrimitiveDeserializer extends JsonDeserializer[Primitive] {
  override def deserialize(jsonParser: JsonParser, deserializationContext: DeserializationContext): Primitive = {
    val json = jsonParser.getCodec.asInstanceOf[ObjectMapper]
    jsonParser.getCurrentToken match {
      case VALUE_TRUE => BooleanPrimitive(true)
      case VALUE_FALSE => BooleanPrimitive(false)
      case VALUE_STRING => StringPrimitive(jsonParser.getValueAsString)
      case VALUE_NUMBER_INT => new NumberPrimitive(jsonParser.getValueAsLong())
      case VALUE_NUMBER_FLOAT => new DoublePrimitive(jsonParser.getValueAsDouble())
      case VALUE_NULL => NullPrimitive
      case START_OBJECT =>
        new ObjectPrimitive(json.readValue(jsonParser, TypeFactory.defaultInstance().constructMapType(classOf[HashMap[_,_]], classOf[String], classOf[Primitive])).asInstanceOf[java.util.Map[String, Primitive]])
      case START_ARRAY =>
        ArrayPrimitive(json.readValue(jsonParser, TypeFactory.defaultInstance().constructArrayType(classOf[Primitive])).asInstanceOf[Array[Primitive]])
    }
  }
}

object SetDeserializer {
  val SetClasses = collection.Map(
    "match" -> classOf[Match],
    "union" -> classOf[Union],
    "intersection" -> classOf[Intersection],
    "difference" -> classOf[Difference],
    "join" -> classOf[Join]
  )
}

class SetDeserializer extends JsonDeserializer[Set] {
  override def deserialize(jsonParser: JsonParser, deserializationContext: DeserializationContext): Set = {
    val loc = jsonParser.getCurrentLocation
    val json = jsonParser.getCodec.asInstanceOf[ObjectMapper]
    val tree = json.readTree(jsonParser).asInstanceOf[ObjectNode]

    val fields = tree.fieldNames.filter(SetDeserializer.SetClasses.keySet.contains(_)).toSeq
    if (fields.length > 1) {
      throw new JsonParseException("Set object cannot contain multiple function names.", loc)
    }

    val clazz = SetDeserializer.SetClasses(fields.head)
    json.treeToValue(tree, clazz)
  }
}