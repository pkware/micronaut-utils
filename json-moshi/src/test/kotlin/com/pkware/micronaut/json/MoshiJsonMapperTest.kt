package com.pkware.micronaut.json

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonStreamConfig
import io.micronaut.json.JsonSyntaxException
import io.micronaut.json.tree.JsonNode
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class MoshiJsonMapperTest {

  private val mapper = MoshiJsonMapper(Moshi.Builder().build())

  @JsonClass(generateAdapter = true)
  data class Foo(val name: String, val count: Int)

  @JsonClass(generateAdapter = true)
  data class Wrapper(val foos: List<Foo>, val label: String)

  @JsonClass(generateAdapter = true)
  data class Numbers(val real: Double, val whole: Long)

  @Test
  fun `round-trips a data class through bytes`() {
    val foo = Foo("widget", 3)
    val bytes = mapper.writeValueAsBytes(foo)
    val result = mapper.readValue(bytes, Argument.of(Foo::class.java))
    assertThat(result).isEqualTo(foo)
  }

  @Test
  fun `round-trips a data class through streams`() {
    val foo = Foo("gadget", 7)
    val out = ByteArrayOutputStream()
    mapper.writeValue(out, Argument.of(Foo::class.java), foo)
    val result = mapper.readValue(ByteArrayInputStream(out.toByteArray()), Argument.of(Foo::class.java))
    assertThat(result).isEqualTo(foo)
  }

  @Test
  fun `round-trips a nested object through bytes`() {
    val wrapper = Wrapper(listOf(Foo("a", 1), Foo("b", 2)), "things")
    val bytes = mapper.writeValueAsBytes(Argument.of(Wrapper::class.java), wrapper)
    val result = mapper.readValue(bytes, Argument.of(Wrapper::class.java))
    assertThat(result).isEqualTo(wrapper)
  }

  @Test
  fun `readValue String overload delegates to bytes`() {
    val result = mapper.readValue("""{"name":"str","count":9}""", Argument.of(Foo::class.java))
    assertThat(result).isEqualTo(Foo("str", 9))
  }

  @Test
  fun `deserializes generic List of Foo with correct element types`() {
    val foos = listOf(Foo("x", 1), Foo("y", 2))
    val type = Argument.of(List::class.java, Foo::class.java) as Argument<List<Foo>>
    val bytes = mapper.writeValueAsBytes(type, foos)
    val result = mapper.readValue(bytes, type)
    assertThat(result).isEqualTo(foos)
    assertThat(result!![0]).isEqualTo(Foo("x", 1))
  }

  @Test
  fun `deserializes generic Map of String to Foo with correct value types`() {
    val map = mapOf("first" to Foo("x", 1), "second" to Foo("y", 2))
    val type = Argument.of(Map::class.java, String::class.java, Foo::class.java) as Argument<Map<String, Foo>>
    val bytes = mapper.writeValueAsBytes(type, map)
    val result = mapper.readValue(bytes, type)
    assertThat(result).isEqualTo(map)
    assertThat(result!!["first"]).isEqualTo(Foo("x", 1))
  }

  @Test
  fun `deserializes nested generic Map of String to List of Foo`() {
    val map = mapOf("group" to listOf(Foo("x", 1), Foo("y", 2)))
    val listOfFoo = Argument.of(List::class.java, Foo::class.java)
    val type = Argument.of(Map::class.java, Argument.of(String::class.java), listOfFoo) as
      Argument<Map<String, List<Foo>>>
    val bytes = mapper.writeValueAsBytes(type, map)
    val result = mapper.readValue(bytes, type)
    assertThat(result).isEqualTo(map)
    assertThat(result!!["group"]!![0]).isEqualTo(Foo("x", 1))
  }

  @Test
  fun `tree round-trips an object`() {
    val foo = Foo("treeval", 42)
    val node = mapper.writeValueToTree(foo)
    val result = mapper.readValueFromTree(node, Argument.of(Foo::class.java))
    assertThat(result).isEqualTo(foo)
  }

  @Test
  fun `tree round-trips a list`() {
    val foos = listOf(Foo("a", 1), Foo("b", 2))
    val type = Argument.of(List::class.java, Foo::class.java) as Argument<List<Foo>>
    val node = mapper.writeValueToTree(type, foos)
    val result = mapper.readValueFromTree(node, type)
    assertThat(result).isEqualTo(foos)
  }

  @Test
  fun `tree round-trips scalars`() {
    val node = mapper.writeValueToTree("hello")
    val result = mapper.readValueFromTree(node, Argument.of(String::class.java))
    assertThat(result).isEqualTo("hello")
  }

  @Test
  fun `writeValueToTree of null returns a null node`() {
    val node = mapper.writeValueToTree(null)
    assertThat(node.isNull).isTrue()
  }

  @Test
  fun `writeValueToTree with typed argument and null returns a null node`() {
    val node = mapper.writeValueToTree(Argument.of(Foo::class.java), null)
    assertThat(node.isNull).isTrue()
  }

  @Test
  fun `writeValueAsBytes of null emits JSON null`() {
    val bytes = mapper.writeValueAsBytes(null)
    assertThat(String(bytes)).isEqualTo("null")
  }

  @Test
  fun `writeValue to stream of null emits JSON null`() {
    val out = ByteArrayOutputStream()
    mapper.writeValue(out, null)
    assertThat(String(out.toByteArray())).isEqualTo("null")
  }

  @Test
  fun `reading the literal null returns null`() {
    val result = mapper.readValue("null".toByteArray(), Argument.of(Foo::class.java))
    assertThat(result).isNull()
  }

  @Test
  fun `malformed JSON via bytes throws JsonSyntaxException`() {
    assertFailure { mapper.readValue("{".toByteArray(), Argument.of(Foo::class.java)) }
      .isInstanceOf(JsonSyntaxException::class)
  }

  @Test
  fun `malformed JSON via input stream throws JsonSyntaxException`() {
    assertFailure { mapper.readValue(ByteArrayInputStream("{foo}".toByteArray()), Argument.of(Foo::class.java)) }
      .isInstanceOf(JsonSyntaxException::class)
  }

  @Test
  fun `round-trips an empty list`() {
    val type = Argument.of(List::class.java, Foo::class.java) as Argument<List<Foo>>
    val bytes = mapper.writeValueAsBytes(type, emptyList())
    val result = mapper.readValue(bytes, type)
    assertThat(result).isEqualTo(emptyList<Foo>())
  }

  @Test
  fun `round-trips an empty map`() {
    val type = Argument.of(Map::class.java, String::class.java, Foo::class.java) as Argument<Map<String, Foo>>
    val bytes = mapper.writeValueAsBytes(type, emptyMap())
    val result = mapper.readValue(bytes, type)
    assertThat(result).isEqualTo(emptyMap<String, Foo>())
  }

  @Test
  fun `writeValueToTree produces an object node with the property values`() {
    val foo = Foo("present", 0)
    val node = mapper.writeValueToTree(foo)
    assertThat(node.isObject).isTrue()
    assertThat(node.get("name")!!.stringValue).isEqualTo("present")
  }

  @Test
  fun `getStreamConfig returns the default`() {
    assertThat(mapper.streamConfig).isEqualTo(JsonStreamConfig.DEFAULT)
  }

  @Test
  fun `reading literal null through tree returns null`() {
    val result = mapper.readValueFromTree(JsonNode.nullNode(), Argument.of(Foo::class.java))
    assertThat(result).isNull()
  }

  @Test
  fun `tree round-trips Double and Long preserving numeric types`() {
    val numbers = Numbers(real = 3.14, whole = 9_000_000_000L)
    val node = mapper.writeValueToTree(numbers)
    val result = mapper.readValueFromTree(node, Argument.of(Numbers::class.java))
    assertThat(result).isEqualTo(numbers)
  }

  @Test
  fun `boolean scalar round-trips through tree`() {
    val node = mapper.writeValueToTree(true)
    assertThat(node.isBoolean).isTrue()
    val result = mapper.readValueFromTree(node, Argument.of(Boolean::class.javaObjectType))
    assertThat(result).isEqualTo(true)
    assertThat(node.isNull).isFalse()
  }
}
