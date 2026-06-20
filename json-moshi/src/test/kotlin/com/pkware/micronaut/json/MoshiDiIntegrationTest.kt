package com.pkware.micronaut.json

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.prop
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.junit.jupiter.api.Test
import java.lang.reflect.Type

/**
 * Verifies the DI-built [JsonMapper] both performs ordinary round-trips and actually applies a
 * consumer-contributed [JsonAdapter.Factory] bean, exercising the wiring in [JsonFactory].
 */
@MicronautTest
class MoshiDiIntegrationTest {

  @Inject
  lateinit var jsonMapper: JsonMapper

  @Test
  fun `DI-built mapper round-trips a representative value`() {
    val value = AllTypesLite("widget", 3, listOf("a", "b"))
    val bytes = jsonMapper.writeValueAsBytes(Argument.of(AllTypesLite::class.java), value)
    val result = jsonMapper.readValue(bytes, Argument.of(AllTypesLite::class.java))
    assertThat(result).isEqualTo(value)
  }

  @Test
  fun `consumer-contributed adapter factory is applied by the DI-built mapper`() {
    // ShoutAdapterFactory (a @Singleton below) serializes a Shout as an uppercased string. If the
    // factory were not registered by JsonFactory, Moshi would fail to serialize this platform type.
    val bytes = jsonMapper.writeValueAsBytes(Argument.of(ShoutHolder::class.java), ShoutHolder(Shout("hello")))
    assertThat(String(bytes)).isEqualTo("""{"message":"HELLO"}""")

    val result = jsonMapper.readValue("""{"message":"world"}""", Argument.of(ShoutHolder::class.java))
    assertThat(result).isNotNull().prop(ShoutHolder::message).isEqualTo(Shout("world"))
  }

  @JsonClass(generateAdapter = true)
  data class AllTypesLite(val name: String, val count: Int, val tags: List<String>)

  @JsonClass(generateAdapter = true)
  data class ShoutHolder(val message: Shout)

  // No @JsonClass: Shout is serialized exclusively through the consumer-contributed
  // ShoutAdapterFactory below, which the DI-built mapper must wire in ahead of the defaults.
  data class Shout(val text: String)

  @Singleton
  class ShoutAdapterFactory : JsonAdapter.Factory {
    override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
      if (type != Shout::class.java) return null
      return object : JsonAdapter<Shout>() {
        override fun toJson(writer: JsonWriter, value: Shout?) {
          writer.value(value?.text?.uppercase())
        }

        override fun fromJson(reader: JsonReader): Shout = Shout(reader.nextString())
      }
    }
  }
}
