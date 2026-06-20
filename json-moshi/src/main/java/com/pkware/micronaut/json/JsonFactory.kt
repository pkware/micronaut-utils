package com.pkware.micronaut.json

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton

/**
 * Builds the application-wide [Moshi] instance used by [MoshiJsonMapper].
 *
 * The [Moshi] is built with no built-in adapters for JVM platform types (`java.math.BigDecimal`,
 * `java.math.BigInteger`, `java.time.*`, etc.). Per Moshi's philosophy, how those types are
 * represented in JSON is the application owner's decision, not this library's — so a platform type
 * without a contributed adapter fails fast rather than being serialized by an opinionated default.
 *
 * Consumers contribute custom [JsonAdapter.Factory] beans (e.g. a `@jakarta.inject.Singleton`);
 * every such bean is registered on the [Moshi.Builder] in injection order, so a consumer adapter
 * takes precedence. Kotlin models are serialized via Moshi codegen
 * (`@JsonClass(generateAdapter = true)`), so no `kotlin-reflect` fallback is registered.
 */
@Factory
internal class JsonFactory(private val adapterFactories: List<JsonAdapter.Factory>) {

  @Singleton
  fun moshi(): Moshi {
    val builder = Moshi.Builder()
    // Consumer-provided factories are the only non-core adapters; they decide how JVM platform types
    // are serialized. Registered in injection order so they take precedence over everything else.
    adapterFactories.forEach(builder::add)
    return builder.build()
  }
}
