package com.pkware.micronaut.json

import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import io.micronaut.context.annotation.BootstrapContextCompatible
import io.micronaut.core.serialize.exceptions.SerializationException
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.json.JsonStreamConfig
import io.micronaut.json.JsonSyntaxException
import io.micronaut.json.tree.JsonNode
import jakarta.inject.Singleton
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.buffer
import okio.sink
import okio.source
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Type

@Singleton
@BootstrapContextCompatible
internal class MoshiJsonMapper(private val moshi: Moshi) : JsonMapper {

  override fun <T : Any> readValueFromTree(tree: JsonNode, type: Argument<T>): T? {
    val adapter = moshi.adapter<Any?>(type.reflectType())
    @Suppress("UNCHECKED_CAST")
    return adapter.fromJsonValue(tree.toPlainValue()) as T?
  }

  override fun <T : Any> readValue(inputStream: InputStream, type: Argument<T>): T? {
    inputStream.source().buffer().use { source ->
      @Suppress("UNCHECKED_CAST")
      return readFrom(source, type) as T?
    }
  }

  override fun <T : Any> readValue(byteArray: ByteArray, type: Argument<T>): T? {
    Buffer().use { buffer ->
      buffer.write(byteArray)
      @Suppress("UNCHECKED_CAST")
      return readFrom(buffer, type) as T?
    }
  }

  override fun writeValueToTree(value: Any?): JsonNode {
    if (value == null) return JsonNode.nullNode()
    val adapter = moshi.adapter<Any>(value.classForMoshi())
    return JsonNode.from(adapter.toJsonValue(value) ?: return JsonNode.nullNode())
  }

  override fun <T : Any> writeValueToTree(type: Argument<T>, value: T?): JsonNode {
    if (value == null) return JsonNode.nullNode()
    val adapter = moshi.adapter<Any?>(type.reflectType())
    return JsonNode.from(adapter.toJsonValue(value) ?: return JsonNode.nullNode())
  }

  override fun writeValue(outputStream: OutputStream, `object`: Any?) {
    // The caller owns the OutputStream, so the sink must not be closed here.
    writeTo(outputStream.sink().buffer(), `object`.classForMoshi(), `object`)
  }

  override fun <T : Any> writeValue(outputStream: OutputStream, type: Argument<T>, `object`: T?) {
    // The caller owns the OutputStream, so the sink must not be closed here.
    writeTo(outputStream.sink().buffer(), type.reflectType(), `object`)
  }

  override fun writeValueAsBytes(`object`: Any?): ByteArray {
    val sink = Buffer()
    writeTo(sink, `object`.classForMoshi(), `object`)
    return sink.readByteArray()
  }

  override fun <T : Any> writeValueAsBytes(type: Argument<T>, `object`: T?): ByteArray {
    val sink = Buffer()
    writeTo(sink, type.reflectType(), `object`)
    return sink.readByteArray()
  }

  override fun getStreamConfig(): JsonStreamConfig = JsonStreamConfig.DEFAULT

  private fun readFrom(source: BufferedSource, type: Argument<*>): Any? {
    val adapter = moshi.adapter<Any?>(type.reflectType())
    return try {
      adapter.fromJson(source)
    } catch (e: JsonEncodingException) {
      throw JsonSyntaxException(e)
    } catch (e: EOFException) {
      throw JsonSyntaxException(e)
    }
  }

  private fun writeTo(sink: BufferedSink, type: Type, value: Any?) {
    val adapter = try {
      moshi.adapter<Any?>(type)
    } catch (expected: Exception) {
      throw SerializationException("Unable to serialize type ${value?.javaClass?.name}", expected)
    }
    adapter.toJson(sink, value)
    sink.flush()
  }
}

/**
 * Bridges a Micronaut [Argument] into a reflective [Type] that Moshi can resolve into a type-aware
 * adapter. Moshi requires canonical parameterized and array types (produced by [Types]) to look up
 * the correct element adapters; a raw [Class] alone would deserialize generic containers into plain
 * [Map]/[List] structures instead of the declared element types.
 */
private fun Argument<*>.reflectType(): Type {
  if (isArray) {
    val component = typeParameters.firstOrNull()?.reflectType() ?: type.componentType
    return Types.arrayOf(component)
  }
  val parameters = typeParameters
  if (parameters.isEmpty()) return type
  val paramTypes = Array(parameters.size) { parameters[it].reflectType() }
  return Types.newParameterizedType(type, *paramTypes)
}

private fun JsonNode.toPlainValue(): Any? = when {
  isNull -> null
  isString -> stringValue
  isNumber -> numberValue
  isBoolean -> booleanValue
  isArray -> values().mapTo(ArrayList()) { it.toPlainValue() }
  isObject -> entries().associateTo(LinkedHashMap()) { it.key to it.value.toPlainValue() }
  else -> error("Unrecognized JsonNode: $this")
}

private fun Any?.classForMoshi(): Class<out Any> = when (this) {
  is Map<*, *> -> Map::class.java
  is List<*> -> List::class.java
  is Set<*> -> Set::class.java
  null -> Any::class.java
  else -> javaClass
}
