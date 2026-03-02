package com.pkware.micronaut.health.hikari

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.micronaut.health.HealthStatus
import io.micronaut.management.health.aggregator.HealthAggregator
import io.micronaut.management.health.indicator.HealthResult
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import java.time.Duration
import java.util.concurrent.Executors

/**
 * Integration tests for [HikariHealthIndicator].
 *
 * Uses a real HikariCP pool connected to an H2 in-memory database to verify end-to-end
 * behavior: connection acquisition, pool stat collection, and result status reporting.
 *
 * Complements [HikariHealthIndicatorTest], which tests error paths using mocks.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HikariHealthIndicatorIntegrationTest {

  private val dataSource = HikariDataSource(
    HikariConfig().apply {
      jdbcUrl = "jdbc:h2:mem:hikaritest;DB_CLOSE_DELAY=-1"
      driverClassName = "org.h2.Driver"
      maximumPoolSize = 5
    },
  )

  private val executor = Executors.newCachedThreadPool()

  // Passthrough aggregator so tests assert on the raw per-pool HealthResult,
  // without DefaultHealthAggregator's nesting behavior.
  private val passthroughAggregator: HealthAggregator<*> = mock()

  private val HealthResult.detailsMap: Map<String, Any>
    @Suppress("UNCHECKED_CAST")
    get() = details as Map<String, Any>

  init {
    whenever(passthroughAggregator.aggregate(any<String>(), any<Publisher<HealthResult>>()))
      .thenAnswer { invocation -> invocation.getArgument<Publisher<HealthResult>>(1) }
  }

  @AfterAll
  fun close() {
    dataSource.close()
    executor.shutdown()
  }

  @Test
  fun `reports UP when database is available`() {
    val result = getHealthResult()

    assertThat(result.status).isEqualTo(HealthStatus.UP)
  }

  @Test
  fun `includes pool stats from real connection`() {
    val result = getHealthResult()

    val details = result.detailsMap
    assertThat(details["pool"]).isNotNull()
    assertThat(details["totalConnections"] as Int).isGreaterThan(0)
  }

  private fun getHealthResult(timeout: Duration = Duration.ofSeconds(10)): HealthResult {
    val indicator = HikariHealthIndicator(
      arrayOf(dataSource),
      null,
      executor,
      passthroughAggregator,
    )
    return requireNotNull(Flux.from(indicator.result).blockFirst(timeout)) {
      "Health check did not emit a result"
    }
  }
}
