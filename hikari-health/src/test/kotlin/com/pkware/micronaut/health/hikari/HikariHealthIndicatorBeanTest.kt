package com.pkware.micronaut.health.hikari

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.micronaut.context.ApplicationContext
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Test

/**
 * Verifies that [HikariHealthIndicator] is registered as a Micronaut bean under the right
 * conditions. These tests would fail if KSP annotation processing were absent — the runtime
 * logic tests in [HikariHealthIndicatorTest] cannot catch that.
 *
 * The datasource is configured in `application-test.properties`, which is only loaded when
 * the `"test"` environment is active (i.e. via [MicronautTest]). Manual [ApplicationContext.run]
 * calls do NOT activate the test environment, so they start without any datasource — enabling
 * clean testing of the [io.micronaut.context.annotation.Requires] conditions.
 */
@MicronautTest(startApplication = false)
class HikariHealthIndicatorBeanTest {

  @Inject
  lateinit var context: ApplicationContext

  @Test
  fun `registers as a bean when Hikari datasource is configured`() {
    assertThat(context.containsBean(HikariHealthIndicator::class.java)).isTrue()
  }

  @Test
  fun `not registered when disabled via property`() {
    // Provide the datasource so @Requires(beans=[DataSource]) is satisfied; only the
    // property flag should prevent registration.
    ApplicationContext.run(
      mapOf(
        "endpoints.health.jdbc.hikari.enabled" to "false",
        "datasources.default.url" to "jdbc:h2:mem:hikari_disabled;DB_CLOSE_DELAY=-1",
        "datasources.default.driver-class-name" to "org.h2.Driver",
        "datasources.default.username" to "sa",
        "datasources.default.password" to "",
      ),
    ).use { ctx ->
      assertThat(ctx.containsBean(HikariHealthIndicator::class.java)).isFalse()
    }
  }

  @Test
  fun `not registered when no DataSource is configured`() {
    // No datasource properties → @Requires(beans=[DataSource::class]) is not satisfied.
    // deduceEnvironment(false) prevents Micronaut from auto-activating the "test" environment
    // (which it detects via micronaut-test-junit5 on the classpath), ensuring application-test
    // .properties is not loaded and no datasource auto-configuration takes place.
    ApplicationContext.builder().deduceEnvironment(false).build().start().use { ctx ->
      assertThat(ctx.containsBean(HikariHealthIndicator::class.java)).isFalse()
    }
  }
}
