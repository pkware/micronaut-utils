package com.pkware.micronaut.json

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.key
import assertk.assertions.prop
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonSyntaxException
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.math.BigInteger

@Suppress("UNCHECKED_CAST")
class MoshiSerdeScenarioTest {

  private val mapper = MoshiJsonMapper(Moshi.Builder().build())

  @JsonClass(generateAdapter = true)
  data class SomeDecimal(val value: BigDecimal)

  @JsonClass(generateAdapter = true)
  data class SomeInteger(val value: BigInteger)

  @JsonClass(generateAdapter = true)
  data class Text(val body: String)

  @Nested
  inner class PrimitivesAndBoxed {

    @Test
    fun `round-trips every primitive, boxed-nullable and String field preserving values`() {
      val value = AllTypes(
        flag = true,
        count = -42,
        total = 9_000_000_000L,
        ratio = 3.5,
        small = 7,
        fraction = 1.25f,
        maybeFlag = false,
        maybeCount = 99,
        label = "everything",
      )
      val bytes = mapper.writeValueAsBytes(Argument.of(AllTypes::class.java), value)
      val result = mapper.readValue(bytes, Argument.of(AllTypes::class.java))
      assertThat(result).isEqualTo(value)
    }

    @Test
    fun `preserves explicit nulls for boxed-nullable fields`() {
      val value = AllTypes(
        flag = false,
        count = 0,
        total = 0L,
        ratio = 0.0,
        small = 0,
        fraction = 0.0f,
        maybeFlag = null,
        maybeCount = null,
        label = "",
      )
      val bytes = mapper.writeValueAsBytes(Argument.of(AllTypes::class.java), value)
      val result = mapper.readValue(bytes, Argument.of(AllTypes::class.java))
      assertThat(result).isNotNull().all {
        prop(AllTypes::maybeFlag).isNull()
        prop(AllTypes::maybeCount).isNull()
      }
    }
  }

  // A consumer-contributed adapter for the JVM platform number types. The module bakes in NO
  // BigDecimal/BigInteger adapter (per Moshi's philosophy, their JSON representation is the
  // application owner's decision), so the consumer registers this on the Moshi.Builder. This
  // consumer chooses to represent them as quoted strings, reading them back at full
  // precision/magnitude.
  object PlatformNumberAdapters {
    @ToJson fun decimalToJson(value: BigDecimal): String = value.toPlainString()

    @FromJson fun decimalFromJson(value: String): BigDecimal = BigDecimal(value)

    @ToJson fun integerToJson(value: BigInteger): String = value.toString()

    @FromJson fun integerFromJson(value: String): BigInteger = BigInteger(value)
  }

  @Nested
  inner class ConsumerContributedNumberAdapters {

    // Built exactly as a consumer would: a plain Moshi.Builder() (no module defaults) plus the
    // consumer's own BigDecimal/BigInteger adapter. Proves the customization contract end-to-end.
    private val consumerMapper = MoshiJsonMapper(Moshi.Builder().add(PlatformNumberAdapters).build())

    @Test
    fun `high-precision BigDecimal round-trips exactly through the contributed adapter`() {
      val holder = SomeDecimal(BigDecimal("12345.12345"))
      val bytes = consumerMapper.writeValueAsBytes(Argument.of(SomeDecimal::class.java), holder)
      val result = consumerMapper.readValue(bytes, Argument.of(SomeDecimal::class.java))
      assertThat(result).isNotNull().prop(SomeDecimal::value).isEqualTo(BigDecimal("12345.12345"))
    }

    @Test
    fun `BigInteger beyond Long MAX_VALUE round-trips exactly through the contributed adapter`() {
      val beyondLong = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.TEN)
      val holder = SomeInteger(beyondLong)
      val bytes = consumerMapper.writeValueAsBytes(Argument.of(SomeInteger::class.java), holder)
      val result = consumerMapper.readValue(bytes, Argument.of(SomeInteger::class.java))
      assertThat(result).isNotNull().prop(SomeInteger::value).isEqualTo(beyondLong)
    }
  }

  @Nested
  inner class Collections {

    private val listType = Argument.of(List::class.java, SomeObject::class.java) as Argument<List<SomeObject?>>

    @Test
    fun `populated list of objects round-trips`() {
      val list = listOf(SomeObject("a"), SomeObject("b"))
      val bytes = mapper.writeValueAsBytes(listType, list)
      val result = mapper.readValue(bytes, listType)
      assertThat(result).isEqualTo(list)
    }

    @Test
    fun `empty JSON array deserializes to an empty list`() {
      val result = mapper.readValue("[]", listType)
      assertThat(result).isNotNull().isEqualTo(emptyList<SomeObject>())
    }

    @Test
    fun `list with a null element preserves the null at its index`() {
      val json = """[{"val":"x"},null,{"val":"z"}]"""
      val result = mapper.readValue(json, listType)
      assertThat(result).isNotNull().all {
        hasSize(3)
        index(0).isEqualTo(SomeObject("x"))
        index(1).isNull()
        index(2).isEqualTo(SomeObject("z"))
      }
    }

    @Test
    fun `nested list of lists round-trips with correct element types`() {
      val type = Argument.of(
        List::class.java,
        Argument.of(List::class.java, SomeObject::class.java),
      ) as Argument<List<List<SomeObject>>>
      val value = listOf(listOf(SomeObject("a")), listOf(SomeObject("b"), SomeObject("c")))
      val bytes = mapper.writeValueAsBytes(type, value)
      val result = mapper.readValue(bytes, type)
      assertThat(result).isNotNull().all {
        index(0).index(0).isEqualTo(SomeObject("a"))
        index(1).hasSize(2)
      }
    }

    @Test
    fun `map of String to object round-trips with correct value types`() {
      val type = Argument.of(Map::class.java, String::class.java, SomeObject::class.java) as
        Argument<Map<String, SomeObject>>
      val value = mapOf("first" to SomeObject("x"), "second" to SomeObject("y"))
      val bytes = mapper.writeValueAsBytes(type, value)
      val result = mapper.readValue(bytes, type)
      assertThat(result).isNotNull().all {
        key("first").isEqualTo(SomeObject("x"))
        key("second").isEqualTo(SomeObject("y"))
      }
    }
  }

  @Nested
  inner class NullAndMissingFields {

    @Test
    fun `nullable field explicitly null in JSON deserializes to null`() {
      val result = mapper.readValue("""{"vals":null}""", Argument.of(ObjectWithArray::class.java))
      assertThat(result).isNotNull().prop(ObjectWithArray::vals).isNull()
    }

    @Test
    fun `empty object into a class with a nullable field yields null`() {
      val result = mapper.readValue("{}", Argument.of(ObjectWithArray::class.java))
      assertThat(result).isNotNull().prop(ObjectWithArray::vals).isNull()
    }

    @Test
    fun `missing field for a non-null Kotlin property throws JsonDataException`() {
      // Moshi's codegen-generated Kotlin adapter enforces non-null constructor parameters: a
      // missing required field is rejected rather than silently defaulted. Because the JSON itself is
      // well-formed, this surfaces as Moshi's JsonDataException (a binding error), NOT a
      // JsonSyntaxException. Documented in README under Known Limitations.
      assertFailure { mapper.readValue("{}", Argument.of(Simple::class.java)) }
        .isInstanceOf(JsonDataException::class)
    }

    @Test
    fun `unknown extra JSON fields are silently ignored`() {
      val result = mapper.readValue("""{"unknown":"x","name":"y"}""", Argument.of(Simple::class.java))
      assertThat(result).isNotNull().prop(Simple::name).isEqualTo("y")
    }
  }

  @Nested
  inner class NestedGenericObjects {

    @Test
    fun `ApiResponse of List of Simple round-trips with real Simple instances`() {
      val type = Argument.of(
        ApiResponse::class.java,
        Argument.of(List::class.java, Simple::class.java),
      ) as Argument<ApiResponse<List<Simple>>>
      val value = ApiResponse(listOf(Simple("alpha"), Simple("beta")))
      val bytes = mapper.writeValueAsBytes(type, value)
      val result = mapper.readValue(bytes, type)
      assertThat(result).isNotNull().prop(ApiResponse<List<Simple>>::content).all {
        index(0).isInstanceOf(Simple::class).isEqualTo(Simple("alpha"))
        index(1).isEqualTo(Simple("beta"))
      }
    }
  }

  @Nested
  inner class Enums {

    @Test
    fun `enum round-trips by name`() {
      val bytes = mapper.writeValueAsBytes(Argument.of(Painted::class.java), Painted(Color.GREEN))
      assertThat(String(bytes)).isEqualTo("""{"color":"GREEN"}""")
      val result = mapper.readValue(bytes, Argument.of(Painted::class.java))
      assertThat(result).isNotNull().prop(Painted::color).isEqualTo(Color.GREEN)
    }

    @Test
    fun `enum inside a collection round-trips`() {
      val type = Argument.of(List::class.java, Color::class.java) as Argument<List<Color>>
      val value = listOf(Color.RED, Color.BLUE)
      val bytes = mapper.writeValueAsBytes(type, value)
      val result = mapper.readValue(bytes, type)
      assertThat(result).isNotNull().containsExactly(Color.RED, Color.BLUE)
    }
  }

  @Nested
  inner class Strings {

    @Test
    fun `unicode and special characters round-trip`() {
      val value = Text("héllo \"wörld\" \n\t ☃ 😀")
      val bytes = mapper.writeValueAsBytes(Argument.of(Text::class.java), value)
      val result = mapper.readValue(bytes, Argument.of(Text::class.java))
      assertThat(result).isEqualTo(value)
    }

    @Test
    fun `empty string round-trips`() {
      val value = Text("")
      val bytes = mapper.writeValueAsBytes(Argument.of(Text::class.java), value)
      val result = mapper.readValue(bytes, Argument.of(Text::class.java))
      assertThat(result).isNotNull().prop(Text::body).isEqualTo("")
    }
  }

  @Nested
  inner class ErrorHandling {

    @Test
    fun `malformed JSON via String throws JsonSyntaxException`() {
      assertFailure { mapper.readValue("{not json", Argument.of(Simple::class.java)) }
        .isInstanceOf(JsonSyntaxException::class)
    }

    @Test
    fun `malformed JSON via ByteArray throws JsonSyntaxException`() {
      assertFailure { mapper.readValue("{not json".toByteArray(), Argument.of(Simple::class.java)) }
        .isInstanceOf(JsonSyntaxException::class)
    }

    @Test
    fun `malformed JSON via InputStream throws JsonSyntaxException`() {
      assertFailure {
        mapper.readValue(ByteArrayInputStream("{not json".toByteArray()), Argument.of(Simple::class.java))
      }.isInstanceOf(JsonSyntaxException::class)
    }

    @Test
    fun `empty input throws JsonSyntaxException`() {
      assertFailure { mapper.readValue("", Argument.of(Simple::class.java)) }
        .isInstanceOf(JsonSyntaxException::class)
    }
  }

  @Nested
  inner class Trees {

    @Test
    fun `navigates an object tree then reads it back to an equal value`() {
      val value = ObjectWithArray(listOf(SomeObject("a"), SomeObject("b")))
      val node = mapper.writeValueToTree(Argument.of(ObjectWithArray::class.java), value)

      assertThat(node.isObject).isTrue()
      val valsNode = node.get("vals")
      assertThat(valsNode).isNotNull()
      assertThat(valsNode!!.isArray).isTrue()
      assertThat(valsNode.isNull).isFalse()
      assertThat(valsNode.size()).isEqualTo(2)
      val firstElement = valsNode.get(0)
      assertThat(firstElement).isNotNull()
      assertThat(firstElement!!.isObject).isTrue()
      assertThat(firstElement.get("val")!!.stringValue).isEqualTo("a")

      val result = mapper.readValueFromTree(node, Argument.of(ObjectWithArray::class.java))
      assertThat(result).isEqualTo(value)
    }

    @Test
    fun `scalar tree exposes its value and round-trips`() {
      val node = mapper.writeValueToTree(7)
      assertThat(node.isNumber).isTrue()
      assertThat(node.intValue).isEqualTo(7)
      val result = mapper.readValueFromTree(node, Argument.of(Int::class.javaObjectType))
      assertThat(result).isEqualTo(7)
    }
  }
}
