package com.pkware.micronaut.json

import com.squareup.moshi.Moshi
import io.micronaut.json.JsonMapper
import io.micronaut.json.JsonMapperSupplier

/**
 * Supplies a [MoshiJsonMapper] during Micronaut's bootstrap phase, where it is discovered via
 * [ServiceLoader][java.util.ServiceLoader] before the dependency-injection container is available.
 *
 * Because dependency injection is not yet running at this point, this supplier builds its own
 * standalone [Moshi] instance rather than reusing the application factory. The mapper is created
 * lazily on first use.
 *
 * Like [JsonFactory], the bootstrap [Moshi] is built with no built-in adapters for JVM platform
 * types (consumers contribute those as DI beans, which are unavailable this early), and it relies on
 * Moshi codegen for Kotlin types with no `kotlin-reflect` fallback, keeping the bootstrap path
 * GraalVM-native friendly.
 *
 * This is a second [Moshi] separate from the DI-managed one, but the cost is negligible. Micronaut's
 * [JsonMapper.createDefault] (the only caller) is invoked from bootstrap/standalone code such as an
 * HTTP client constructed without a DI codec registry; a DI-injected client uses the registry from
 * the context instead, so in a typical application this lazy mapper is never built. When it is, a
 * plain [Moshi] holds only references to Moshi's shared, stateless built-in adapter factories plus a
 * small cache that fills lazily for the few types serialized during bootstrap — far lighter than the
 * official serde supplier, which stands up an entire separate `ApplicationContext` for the same
 * purpose. Converging onto the DI mapper would require static context-holder state for no meaningful
 * memory saving, so the two instances are intentional.
 */
public class MoshiJsonMapperSupplier : JsonMapperSupplier {
  private val mapper by lazy {
    MoshiJsonMapper(Moshi.Builder().build())
  }

  override fun get(): JsonMapper = mapper
}
