package com.pkware.micronaut.json

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.junit.jupiter.api.Test
import java.lang.reflect.Type
import java.math.BigDecimal

/**
 * Verifies that the application context wires up the Moshi-backed JSON beans. These would fail if
 * [JsonFactory] or the [JsonMapper] registration were broken, which the unit tests cannot catch.
 */
@MicronautTest
class JsonFactoryBeanTest {

  @Inject
  lateinit var context: ApplicationContext

  @Inject
  lateinit var jsonMapper: JsonMapper

  @JsonClass(generateAdapter = true)
  data class Money(val amount: BigDecimal)

  @Test
  fun `provides a Moshi bean`() {
    assertThat(context.getBean(Moshi::class.java)).isInstanceOf(Moshi::class)
  }

  @Test
  fun `provides a MoshiJsonMapper as the JsonMapper bean`() {
    assertThat(context.getBean(JsonMapper::class.java)).isInstanceOf(MoshiJsonMapper::class)
  }

  @Test
  fun `DI-built JsonMapper round-trips BigDecimal via a consumer-contributed adapter factory`() {
    // The module bakes in no BigDecimal adapter. BigDecimalAdapterFactory (a @Singleton below)
    // supplies one; JsonFactory must wire every JsonAdapter.Factory bean into the Moshi. If it did
    // not, Moshi would fail fast on this platform type. This proves the end-to-end extensibility
    // story: an application contributes its own number representation as a DI bean.
    val money = Money(BigDecimal("12345.6789012345678901234567890"))
    val bytes = jsonMapper.writeValueAsBytes(Argument.of(Money::class.java), money)
    val result = jsonMapper.readValue(bytes, Argument.of(Money::class.java))
    assertThat(result).isEqualTo(money)
  }

  @Singleton
  class BigDecimalAdapterFactory : JsonAdapter.Factory {
    override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
      if (type != BigDecimal::class.java) return null
      return object : JsonAdapter<BigDecimal>() {
        override fun toJson(writer: JsonWriter, value: BigDecimal?) {
          if (value == null) writer.nullValue() else writer.value(value)
        }

        override fun fromJson(reader: JsonReader): BigDecimal = BigDecimal(reader.nextString())
      }
    }
  }
}
