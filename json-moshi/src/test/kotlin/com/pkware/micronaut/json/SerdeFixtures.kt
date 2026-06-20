package com.pkware.micronaut.json

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// Fixtures mirroring the scenarios covered by Micronaut serde-tck's AbstractBasicSerdeSpec, recreated
// as Kotlin data classes/enums. Each is annotated with @JsonClass(generateAdapter = true) so the
// moshi-kotlin-codegen KSP processor generates a reflection-free adapter (this module registers no
// KotlinJsonAdapterFactory). Enums need no annotation: Moshi handles them via its built-in
// reflection-free EnumJsonAdapter.

@JsonClass(generateAdapter = true)
internal data class Simple(val name: String)

@JsonClass(generateAdapter = true)
internal data class SomeObject(@Json(name = "val") val value: String)

@JsonClass(generateAdapter = true)
internal data class ObjectWithArray(val vals: List<SomeObject>?)

@JsonClass(generateAdapter = true)
internal data class ApiResponse<T>(val content: T)

@JsonClass(generateAdapter = true)
internal data class AllTypes(
  val flag: Boolean,
  val count: Int,
  val total: Long,
  val ratio: Double,
  val small: Short,
  val fraction: Float,
  val maybeFlag: Boolean?,
  val maybeCount: Int?,
  val label: String,
)

internal enum class Color { RED, GREEN, BLUE }

@JsonClass(generateAdapter = true)
internal data class Painted(val color: Color)
