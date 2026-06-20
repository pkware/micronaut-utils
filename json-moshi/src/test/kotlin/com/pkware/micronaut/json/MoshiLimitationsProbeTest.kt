package com.pkware.micronaut.json

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.cause
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.messageContains
import com.squareup.moshi.Json
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import io.micronaut.core.serialize.exceptions.SerializationException
import io.micronaut.core.type.Argument
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Probes the boundaries where Moshi diverges from Micronaut serde. Each test asserts the ACTUAL
 * observed behavior (not aspirational) and is the evidence behind the json-moshi README's Known
 * Limitations section. Behaviors that genuinely fail are asserted to fail.
 */
@Suppress("UNCHECKED_CAST")
class MoshiLimitationsProbeTest {

  private val mapper = MoshiJsonMapper(Moshi.Builder().build())

  internal enum class Status {
    @Json(name = "in-progress")
    IN_PROGRESS,

    @Json(name = "done")
    DONE,
  }

  @Nested
  inner class UnannotatedKotlinClass {

    @Test
    fun `serializing an unannotated Kotlin class fails fast`() {
      // This module registers no KotlinJsonAdapterFactory (no kotlin-reflect at runtime). A Kotlin
      // class without @JsonClass(generateAdapter = true) therefore has no generated adapter, and
      // Moshi core's ClassJsonAdapter refuses to reflectively serialize a class carrying
      // kotlin.Metadata, throwing IllegalArgumentException ("Cannot serialize Kotlin type ..."). The
      // mapper surfaces adapter-lookup failures as a SerializationException wrapping that cause.
      assertFailure { mapper.writeValueAsBytes(NotAnnotated("x")) }
        .isInstanceOf(SerializationException::class)
        .cause()
        .isNotNull()
        .isInstanceOf(IllegalArgumentException::class)
        .messageContains("Cannot serialize Kotlin type")
    }
  }

  @Nested
  inner class PlatformTypeWithoutContributedAdapter {

    @Test
    fun `serializing a JVM platform type without a contributed adapter fails fast`() {
      // This module bakes in NO BigDecimal/BigInteger (or other JVM platform-type) adapter: per
      // Moshi's philosophy, their JSON representation is the application owner's decision. Without a
      // consumer-contributed JsonAdapter, Moshi core refuses to serialize a platform class, throwing
      // IllegalArgumentException ("Platform class java.math.BigDecimal requires explicit JsonAdapter
      // to be registered"). The mapper surfaces adapter-lookup failures as a SerializationException
      // wrapping that cause. This is the desired, honest contract — see README.
      assertFailure { mapper.writeValueAsBytes(java.math.BigDecimal("1.5")) }
        .isInstanceOf(SerializationException::class)
        .cause()
        .isNotNull()
        .isInstanceOf(IllegalArgumentException::class)
        .messageContains("requires explicit JsonAdapter")
    }
  }

  @Nested
  inner class JavaRecords {

    @Test
    fun `Java records round-trip via Moshi's reflective ClassJsonAdapter`() {
      // Empirically, on Java 25 Moshi's reflective ClassJsonAdapter both serializes a record (via
      // its accessor-backed fields) and reconstructs it correctly, so a simple record round-trips.
      // This is reflection-based, not record-aware: it does not honor a record's compact/canonical
      // constructor or validation, and it relies on field access that can be fragile under strong
      // encapsulation. Consumers needing robust record support should contribute a JsonAdapter.
      val bytes = mapper.writeValueAsBytes(Argument.of(JavaRecordFixture::class.java), JavaRecordFixture("x", 1))
      assertThat(String(bytes)).isEqualTo("""{"name":"x","count":1}""")

      val result = mapper.readValue(bytes, Argument.of(JavaRecordFixture::class.java))
      assertThat(result).isNotNull().isEqualTo(JavaRecordFixture("x", 1))
    }
  }

  @Nested
  inner class JavaPojos {

    @Test
    fun `plain Java POJOs round-trip via Moshi's reflective ClassJsonAdapter`() {
      // Unlike records, a mutable POJO with a no-arg constructor round-trips correctly, because
      // ClassJsonAdapter can instantiate it and assign its non-final fields.
      val pojo = JavaPojoFixture().apply {
        name = "x"
        count = 1
      }
      val bytes = mapper.writeValueAsBytes(Argument.of(JavaPojoFixture::class.java), pojo)
      val result = mapper.readValue(bytes, Argument.of(JavaPojoFixture::class.java))
      assertThat(result).isNotNull()
      assertThat(result!!.name).isEqualTo("x")
      assertThat(result.count).isEqualTo(1)
    }
  }

  @Nested
  inner class MapWithEnumKeys {

    @Test
    fun `Map with enum keys serializes the key via its name`() {
      // Moshi serializes a non-String map key by writing its toString(), so enum keys become their
      // name. This works for serialization. Deserialization back to an enum-keyed map is covered
      // separately.
      val type = Argument.of(Map::class.java, Color::class.java, String::class.java) as
        Argument<Map<Color, String>>
      val bytes = mapper.writeValueAsBytes(type, mapOf(Color.RED to "stop"))
      assertThat(String(bytes)).isEqualTo("""{"RED":"stop"}""")
    }

    @Test
    fun `Map with enum keys round-trips back to an enum-keyed map`() {
      val type = Argument.of(Map::class.java, Color::class.java, String::class.java) as
        Argument<Map<Color, String>>
      val result = mapper.readValue("""{"RED":"stop"}""", type)
      assertThat(result).isNotNull().isEqualTo(mapOf(Color.RED to "stop"))
    }
  }

  @Nested
  inner class CaseInsensitiveEnums {

    @Test
    fun `enum deserialization is case-sensitive and an unknown name throws JsonDataException`() {
      // Moshi matches enum constants exactly (or via @Json names); a case mismatch is a data error,
      // surfaced as JsonDataException (a RuntimeException), NOT JsonSyntaxException. The mapper only
      // wraps malformed-syntax errors into JsonSyntaxException; structurally-valid JSON that fails to
      // bind passes through as Moshi's JsonDataException.
      assertFailure {
        mapper.readValue("""{"color":"red"}""", Argument.of(Painted::class.java))
      }.isInstanceOf(JsonDataException::class)
    }

    @Test
    fun `custom enum JSON values are supported through an explicit @Json annotation`() {
      // Moshi maps enum constants to JSON via @Json(name=...), the supported mechanism for custom
      // values; there is no built-in case-insensitive or @JsonValue-style coercion.
      val bytes = mapper.writeValueAsBytes(Argument.of(Status::class.java), Status.IN_PROGRESS)
      assertThat(String(bytes)).isEqualTo("\"in-progress\"")
      val result = mapper.readValue("\"in-progress\"", Argument.of(Status::class.java))
      assertThat(result).isNotNull().isEqualTo(Status.IN_PROGRESS)
    }
  }
}

// A throwaway Kotlin class with NO @JsonClass annotation, used to prove that without
// KotlinJsonAdapterFactory (and thus without kotlin-reflect) an unannotated Kotlin type cannot be
// serialized and fails fast. Top-level so it is unambiguously a plain Kotlin class.
internal data class NotAnnotated(val name: String)
