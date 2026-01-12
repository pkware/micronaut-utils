package com.example.assisted

import com.pkware.micronaut.assisted.Assisted

/**
 * Factory interface that uses @Assisted from the assisted-inject JAR.
 *
 * Unlike the Java integration test, this does NOT need @Introduction directly.
 * KSP correctly processes meta-annotations from compiled JARs, so the @Introduction
 * on @Assisted is detected and the factory implementation is generated.
 *
 * This proves Kotlin/KSP users get a cleaner experience - just @Assisted is enough.
 */
@Assisted
interface GreetingFactory {
  fun create(name: String): Greeting
}
