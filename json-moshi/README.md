# json-moshi

A [Moshi](https://github.com/square/moshi)-backed implementation of Micronaut's
`io.micronaut.json.JsonMapper`. It registers as a `@Primary` `JsonMapper` bean (and via
`JsonMapperSupplier` during bootstrap), so Micronaut serialization/deserialization runs through
Moshi instead of the built-in serde or Jackson mappers.

This module is **reflection-free for Kotlin types**: it does **not** register
`KotlinJsonAdapterFactory` and therefore pulls in no `org.jetbrains.kotlin:kotlin-reflect` (~1.5 MB)
at runtime. Kotlin models are serialized through Moshi *codegen* — generated `JsonAdapter` classes
resolved at runtime via `Class.forName`. This keeps the runtime small and GraalVM-native friendly,
aligned with Micronaut's AOT/build-time-first philosophy.

This document records the behavior that the test suite actually exercises, including the places
where Moshi diverges from Micronaut serde. Every claim below is backed by a test in
`src/test` — see `MoshiSerdeScenarioTest`, `MoshiLimitationsProbeTest`, `MoshiDiIntegrationTest`,
and `MoshiJsonMapperTest`.

## Consumer setup for Kotlin models

Because there is no `kotlin-reflect` fallback, every Kotlin model you serialize must have a
generated adapter. Annotate the class and apply the Moshi codegen KSP processor:

```kotlin
// build.gradle.kts
plugins {
  id("com.google.devtools.ksp")
}

dependencies {
  ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.2")
}
```

```kotlin
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Order(val id: String, val quantity: Int)
```

KSP generates an `OrderJsonAdapter` at build time; Moshi resolves it at runtime with no reflection.
Codegen cannot process **local** or **`inner`** classes — model classes must be top-level or
static-nested (a plain `data class` nested in another class, not `inner`).

### Unannotated Kotlin types fail fast

A Kotlin class **without** `@JsonClass(generateAdapter = true)` has no generated adapter. Moshi
core's `ClassJsonAdapter` refuses to reflectively serialize a class carrying `kotlin.Metadata`,
throwing `IllegalArgumentException` whose message is *"Cannot serialize Kotlin type ... Please use
KotlinJsonAdapterFactory from the moshi-kotlin artifact or use code gen from the
moshi-kotlin-codegen artifact."* The mapper surfaces this as a
`io.micronaut.core.serialize.exceptions.SerializationException` wrapping that cause. This fail-fast
behavior is intentional — it is the signal that you forgot to annotate/codegen a model. Verified by
`MoshiLimitationsProbeTest`.

## JVM platform types are not serialized by default

This module bakes in **no** adapters for JVM platform types — `java.math.BigDecimal`,
`java.math.BigInteger`, `java.time.*`, and the like. Per
[Moshi's philosophy](https://github.com/square/moshi#custom-type-adapters), deciding how such a
platform type is represented in JSON (a number? a string? an object? what precision/format?) is the
**application owner's** decision, not this library's. The module refuses to make that choice for you.

Consequently, serializing a platform type that has no contributed adapter **fails fast**: Moshi core
throws `IllegalArgumentException` (*"Platform class java.math.BigDecimal requires explicit JsonAdapter
to be registered"*), which the mapper surfaces as a
`io.micronaut.core.serialize.exceptions.SerializationException` wrapping that cause. This is the
intended, honest contract — verified by `MoshiLimitationsProbeTest`.

To serialize such a type, contribute a `JsonAdapter` / `JsonAdapter.Factory` bean (see
[Contributing custom adapters](#contributing-custom-adapters) below). `JsonFactory` wires every such
bean into the application `Moshi`. `MoshiSerdeScenarioTest` and `JsonFactoryBeanTest` demonstrate the
full `BigDecimal`/`BigInteger` round-trip through a consumer-contributed adapter.

## Supported out of the box

The `Moshi` instance is built by `JsonFactory` (or `MoshiJsonMapperSupplier` during bootstrap) as a
plain `Moshi.Builder().build()` plus any consumer-contributed adapter beans — no opinionated
defaults. The following work without any extra configuration:

- **Kotlin data classes annotated with `@JsonClass(generateAdapter = true)`** — the primary path;
  serialized/deserialized by codegen-generated adapters, including all primitive and boxed-nullable
  numeric types plus `String`.
- **Collections** — `List` (populated, empty, nested list-of-lists, and lists containing `null`
  elements), with element types resolved from the Micronaut `Argument`.
- **Maps with `String` keys** — `Map<String, T>` with the declared value type preserved.
- **Enums by name** — round-tripped using the constant name via Moshi's built-in, reflection-free
  `EnumJsonAdapter`; enums need **no** `@JsonClass` annotation. Custom JSON values are supported by
  annotating constants with `@com.squareup.moshi.Json(name = "...")`.
- **Generics** — parameterized types such as `ApiResponse<List<Simple>>` and
  `Map<String, List<Foo>>` are resolved through an `Argument` → `java.lang.reflect.Type` bridge so
  inner elements deserialize to their real declared types (not raw `Map`/`List`). Generic models
  annotated with `@JsonClass(generateAdapter = true)` are supported; codegen emits a
  `(Moshi, Type[])` constructor for them.
- **Null omission and reading** — a `null` value writes a JSON `null`; an explicit `null` in JSON
  and a missing field for a *nullable* Kotlin property both deserialize to `null`.
- **Unknown / extra JSON fields** — silently ignored during deserialization.
- **JSON trees** — `writeValueToTree` / `readValueFromTree` round-trip through
  `io.micronaut.json.tree.JsonNode`, preserving object/array/scalar structure and numeric types.

### Contributing custom adapters

Consumers contribute `com.squareup.moshi.JsonAdapter` / `com.squareup.moshi.JsonAdapter.Factory`
beans as `@jakarta.inject.Singleton`s. `JsonFactory` injects `List<JsonAdapter.Factory>` and
registers every such bean on the `Moshi.Builder` in injection order, so a consumer adapter takes
precedence. This is the supported mechanism for **any** type the module does not handle for you —
JVM platform types (`BigDecimal`, `BigInteger`, `java.time`, …), Java types, or Kotlin types you
cannot annotate. Verified by `MoshiDiIntegrationTest` and `JsonFactoryBeanTest`.

```kotlin
import com.squareup.moshi.*
import jakarta.inject.Singleton
import java.lang.reflect.Type
import java.math.BigDecimal

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
```

## Known limitations vs Micronaut serde

These are Moshi-by-design behaviors, not bugs in this module. Where a behavior is unsupported,
contribute a `JsonAdapter` / `JsonAdapter.Factory` bean (see above).

### Exception type for data-binding errors

The mapper wraps only *malformed-syntax* failures (Moshi's `JsonEncodingException`, and `EOFException`
for empty/truncated input) into `io.micronaut.json.JsonSyntaxException`, matching the serde TCK's
`JsonSyntaxExceptionTest` (which only asserts `JsonSyntaxException` for genuinely malformed JSON like
`{foo}`).

A *binding* failure on structurally-valid JSON — a missing required (non-null) field, or an enum
value that matches no constant — surfaces as Moshi's `com.squareup.moshi.JsonDataException` (a
`RuntimeException`), **not** `JsonSyntaxException`. Micronaut serde reports these differently (and may
apply defaults). Callers that need to catch binding errors should expect `JsonDataException`.

### Missing field for a non-null Kotlin property

The codegen-generated Kotlin adapter enforces non-null constructor parameters: deserializing `{}`
into a class with a required non-null property throws `JsonDataException` rather than substituting a
default. Serde can fall back to defaults in some configurations.

### Enums are case-sensitive; no `@JsonValue`-style coercion

Enum matching is exact (by constant name, or by `@Json(name = ...)`). A case mismatch (e.g. `"red"`
for `RED`) throws `JsonDataException`. There is no built-in case-insensitive matching or
`@JsonValue`-style custom-value coercion beyond `@Json(name = ...)` on each constant.

### `Map` with non-`String` keys

Moshi serializes a non-`String` map key by writing its `toString()`. For an enum key this yields the
constant name, and a `Map<EnumKey, V>` does round-trip correctly in this module's tests. However,
this relies on `toString()`/parse symmetry and is not general: arbitrary non-`String` key types
without that symmetry are not supported out of the box and need a custom adapter.

### Java records and plain Java POJOs

This module targets Kotlin data classes. Java types are handled (if at all) only by Moshi's
reflective `ClassJsonAdapter` / `RecordJsonAdapter` (Java reflection, **not** `kotlin-reflect`), not
by any bean-aware adapter:

- **Plain mutable Java POJOs** (no-arg constructor + fields) round-trip via reflection.
- **Java records** also round-trip in a simple probe on Java 25 via reflective field access, but
  this is *not* record-aware: it ignores the canonical/compact constructor and any validation, and
  depends on reflective field access that is fragile under strong encapsulation (later JDKs / module
  configurations may break it).

For GraalVM native image, this Java reflection requires explicit reflection-metadata registration;
prefer codegen or an explicit `JsonAdapter` for native targets. For any Java type where you need
reliable, validated mapping, contribute a `JsonAdapter` / `JsonAdapter.Factory` bean. Do not rely on
the reflective fallback for production Java types.
