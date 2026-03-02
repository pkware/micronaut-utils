package com.pkware.micronaut.health.hikari

import assertk.assertThat
import assertk.assertions.containsAtLeast
import assertk.assertions.doesNotContain
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThan
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.HikariPoolMXBean
import io.micronaut.health.HealthStatus
import io.micronaut.management.health.aggregator.HealthAggregator
import io.micronaut.management.health.indicator.HealthResult
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import java.sql.Connection
import java.sql.SQLException
import java.time.Duration
import java.util.concurrent.Executors
import javax.sql.DataSource

/**
 * Unit tests for [HikariHealthIndicator].
 *
 * Uses mocked [DataSource]/[HikariDataSource] to verify error paths that cannot be
 * triggered against a healthy database. Integration tests with a real HikariCP pool
 * are in [HikariHealthIndicatorIntegrationTest].
 */
class HikariHealthIndicatorTest {

  private val mockMxBean: HikariPoolMXBean = mock {
    on { totalConnections } doReturn 10
    on { activeConnections } doReturn 10
    on { idleConnections } doReturn 0
    on { threadsAwaitingConnection } doReturn 5
  }

  private val mockHikari: HikariDataSource = mock {
    on { poolName } doReturn "HikariPool-test"
    on { hikariPoolMXBean } doReturn mockMxBean
  }

  private val mockDataSource: DataSource = mock {
    on { unwrap(HikariDataSource::class.java) } doReturn mockHikari
  }

  // Passthrough aggregator: returns the merged publisher as individual results,
  // bypassing the real DefaultHealthAggregator's wrapping/nesting behavior.
  // This lets single-pool tests assert directly on the per-pool HealthResult.
  private val passthroughAggregator: HealthAggregator<*> = mock {
    on { aggregate(any<String>(), any<Publisher<HealthResult>>()) } doReturn Flux.empty()
  }

  private val testExecutor = Executors.newCachedThreadPool()

  private val HealthResult.detailsMap: Map<String, Any>
    @Suppress("UNCHECKED_CAST")
    get() = details as Map<String, Any>

  init {
    // Wire the mock to return the publisher it receives, so results pass through unchanged.
    whenever(passthroughAggregator.aggregate(any<String>(), any<Publisher<HealthResult>>()))
      .thenAnswer { invocation -> invocation.getArgument<Publisher<HealthResult>>(1) }
  }

  @Test
  fun `reports UP when connection is acquired successfully`() {
    val mockConnection: Connection = mock()
    whenever(mockHikari.connection).thenReturn(mockConnection)

    val result = getHealthResult()

    assertThat(result.status).isEqualTo(HealthStatus.UP)
    assertThat(result.detailsMap.keys).doesNotContain("error")
  }

  @Test
  fun `reports DOWN when connection throws SQLException`() {
    whenever(mockHikari.connection).thenThrow(SQLException("Connection refused"))

    val result = getHealthResult()

    assertThat(result.status).isEqualTo(HealthStatus.DOWN)
  }

  @Test
  fun `includes pool stats on success`() {
    val mockConnection: Connection = mock()
    whenever(mockHikari.connection).thenReturn(mockConnection)

    val details = getHealthResult().detailsMap

    assertThat(details["pool"]).isEqualTo("HikariPool-test")
    assertThat(details["totalConnections"]).isEqualTo(10)
    assertThat(details["activeConnections"]).isEqualTo(10)
    assertThat(details["idleConnections"]).isEqualTo(0)
    assertThat(details["threadsAwaitingConnection"]).isEqualTo(5)
  }

  @Test
  fun `includes pool stats even when connection fails`() {
    whenever(mockHikari.connection).thenThrow(SQLException("Connection refused"))

    val details = getHealthResult().detailsMap

    assertThat(details["pool"]).isEqualTo("HikariPool-test")
    assertThat(details["totalConnections"]).isEqualTo(10)
    assertThat(details["activeConnections"]).isEqualTo(10)
    assertThat(details["idleConnections"]).isEqualTo(0)
    assertThat(details["threadsAwaitingConnection"]).isEqualTo(5)
  }

  @Test
  fun `reports DOWN when connection close throws`() {
    val badConnection: Connection = mock {
      on { close() } doThrow SQLException("Connection reset during close")
    }
    whenever(mockHikari.connection).thenReturn(badConnection)

    val result = getHealthResult()

    assertThat(result.status).isEqualTo(HealthStatus.DOWN)
  }

  @Test
  fun `reports DOWN when connection acquisition blocks beyond timeout`() {
    whenever(mockHikari.connection).thenAnswer {
      Thread.sleep(10_000)
      mock<Connection>()
    }

    val result = getHealthResult()

    assertThat(result.status).isEqualTo(HealthStatus.DOWN)
  }

  @Test
  fun `timeout fires within expected duration`() {
    whenever(mockHikari.connection).thenAnswer {
      Thread.sleep(10_000)
      mock<Connection>()
    }

    val start = System.currentTimeMillis()
    getHealthResult()
    val elapsed = System.currentTimeMillis() - start

    // Should complete in roughly 2s (the configured timeout), not 10s (the mock sleep).
    // Allow generous margin for CI but must be well under the mock's 10s sleep.
    assertThat(elapsed).isLessThan(5_000L)
  }

  @Test
  fun `uses pool name as health result key`() {
    val mockConnection: Connection = mock()
    whenever(mockHikari.connection).thenReturn(mockConnection)

    val result = getHealthResult()

    assertThat(result.name).isEqualTo("HikariPool-test")
  }

  @Test
  fun `checks multiple Hikari pools`() {
    val mxBean2: HikariPoolMXBean = mock {
      on { totalConnections } doReturn 5
      on { activeConnections } doReturn 2
      on { idleConnections } doReturn 3
      on { threadsAwaitingConnection } doReturn 0
    }
    val hikari2: HikariDataSource = mock {
      on { poolName } doReturn "HikariPool-secondary"
      on { hikariPoolMXBean } doReturn mxBean2
    }
    val dataSource2: DataSource = mock {
      on { unwrap(HikariDataSource::class.java) } doReturn hikari2
    }

    val mockConnection: Connection = mock()
    whenever(mockHikari.connection).thenReturn(mockConnection)
    whenever(hikari2.connection).thenReturn(mockConnection)

    val results = getAllHealthResults(mockDataSource, dataSource2)

    assertThat(results).hasSize(2)
    // Flux.merge() does not guarantee emission order — use order-independent assertions.
    assertThat(results.map { it.name }).containsAtLeast("HikariPool-test", "HikariPool-secondary")
    assertThat(results.map { it.status }).containsAtLeast(HealthStatus.UP)
  }

  @Test
  fun `skips non-Hikari DataSources`() {
    val nonHikariDataSource: DataSource = mock {
      on { unwrap(HikariDataSource::class.java) } doThrow SQLException("Not a wrapper for HikariDataSource")
    }

    val mockConnection: Connection = mock()
    whenever(mockHikari.connection).thenReturn(mockConnection)

    val results = getAllHealthResults(mockDataSource, nonHikariDataSource)

    // Only the Hikari-backed DataSource should produce a result.
    assertThat(results).hasSize(1)
    assertThat(results[0].name).isEqualTo("HikariPool-test")
  }

  @Test
  fun `returns empty when no DataSources are Hikari`() {
    val nonHikariDataSource: DataSource = mock {
      on { unwrap(HikariDataSource::class.java) } doThrow SQLException("Not a wrapper")
    }

    val results = getAllHealthResults(nonHikariDataSource)

    assertThat(results).isEmpty()
  }

  @Test
  fun `returns empty when DataSource array is empty`() {
    val results = getAllHealthResults()

    assertThat(results).isEmpty()
  }

  @Test
  fun `reports individual pool status when one pool is down`() {
    val mxBean2: HikariPoolMXBean = mock {
      on { totalConnections } doReturn 5
      on { activeConnections } doReturn 5
      on { idleConnections } doReturn 0
      on { threadsAwaitingConnection } doReturn 3
    }
    val hikari2: HikariDataSource = mock {
      on { poolName } doReturn "HikariPool-secondary"
      on { hikariPoolMXBean } doReturn mxBean2
    }
    val dataSource2: DataSource = mock {
      on { unwrap(HikariDataSource::class.java) } doReturn hikari2
    }

    val mockConnection: Connection = mock()
    whenever(mockHikari.connection).thenReturn(mockConnection)
    whenever(hikari2.connection).thenThrow(SQLException("Connection refused"))

    val results = getAllHealthResults(mockDataSource, dataSource2)

    assertThat(results).hasSize(2)
    val byName = results.associateBy { it.name }
    assertThat(byName["HikariPool-test"]!!.status).isEqualTo(HealthStatus.UP)
    assertThat(byName["HikariPool-secondary"]!!.status).isEqualTo(HealthStatus.DOWN)
  }

  @Test
  fun `aggregator receives name hikariCP`() {
    val mockConnection: Connection = mock()
    whenever(mockHikari.connection).thenReturn(mockConnection)

    var capturedName: String? = null
    val capturingAggregator: HealthAggregator<*> = mock()
    whenever(capturingAggregator.aggregate(any<String>(), any<Publisher<HealthResult>>()))
      .thenAnswer { invocation ->
        capturedName = invocation.getArgument(0)
        invocation.getArgument<Publisher<HealthResult>>(1)
      }

    val indicator = HikariHealthIndicator(
      arrayOf(mockDataSource),
      null,
      testExecutor,
      capturingAggregator,
    )
    Flux.from(indicator.result).blockFirst(Duration.ofSeconds(10))

    assertThat(capturedName).isEqualTo("hikariCP")
  }

  private fun getHealthResult(timeout: Duration = Duration.ofSeconds(10)): HealthResult {
    val indicator = HikariHealthIndicator(
      arrayOf(mockDataSource),
      null,
      testExecutor,
      passthroughAggregator,
    )
    return requireNotNull(Flux.from(indicator.result).blockFirst(timeout)) {
      "Health check did not emit a result"
    }
  }

  private fun getAllHealthResults(vararg dataSources: DataSource): List<HealthResult> {
    val indicator = HikariHealthIndicator(
      arrayOf(*dataSources),
      null,
      testExecutor,
      passthroughAggregator,
    )
    return Flux.from(indicator.result).collectList().block(Duration.ofSeconds(10)) ?: emptyList()
  }
}
