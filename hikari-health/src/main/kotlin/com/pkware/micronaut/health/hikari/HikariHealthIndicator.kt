package com.pkware.micronaut.health.hikari

import com.zaxxer.hikari.HikariDataSource
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.micronaut.core.util.StringUtils
import io.micronaut.health.HealthStatus
import io.micronaut.jdbc.DataSourceResolver
import io.micronaut.management.endpoint.health.HealthEndpoint
import io.micronaut.management.health.aggregator.HealthAggregator
import io.micronaut.management.health.indicator.HealthIndicator
import io.micronaut.management.health.indicator.HealthResult
import io.micronaut.scheduling.TaskExecutors
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.sql.SQLException
import java.time.Duration
import java.util.concurrent.ExecutorService
import javax.sql.DataSource

/**
 * Health indicator for HikariCP connection pools.
 *
 * Reports [HealthStatus.DOWN] when a connection cannot be acquired in a timely manner.
 * Includes pool stats (active/idle connections, threads awaiting) that
 * [io.micronaut.management.health.indicator.jdbc.JdbcIndicator] does not provide.
 *
 * Enable or disable via the `management.health.jdbc.hikari.enabled` property.
 *
 * @param dataSources all configured [DataSource] beans, auto-collected by Micronaut.
 * @param dataSourceResolver unwraps Micronaut's `ContextualConnection` proxy to the real
 *   [DataSource]. `null` falls back to [DataSourceResolver.DEFAULT] (identity).
 * @param blockingExecutor Micronaut's blocking I/O thread pool, same pool used by
 *   [io.micronaut.management.health.indicator.jdbc.JdbcIndicator].
 * @param healthAggregator merges per-pool results under a single `"hikariCP"` parent
 *   when multiple pools exist.
 */
@Singleton
@Requires(property = HealthEndpoint.PREFIX + ".jdbc.hikari.enabled", notEquals = StringUtils.FALSE)
@Requires(beans = [HealthEndpoint::class])
@Requires(beans = [DataSource::class])
public class HikariHealthIndicator(
  dataSources: Array<DataSource>,
  @Nullable dataSourceResolver: DataSourceResolver?,
  @param:Named(TaskExecutors.BLOCKING) private val blockingExecutor: ExecutorService,
  private val healthAggregator: HealthAggregator<*>,
) : HealthIndicator {

  private val resolver = dataSourceResolver ?: DataSourceResolver.DEFAULT

  // Resolve each DataSource through Micronaut's proxy layer, then keep only those backed by
  // HikariCP. Non-Hikari DataSources (e.g. embedded H2 without a pool) are silently skipped.
  private val hikariSources: List<HikariDataSource> = dataSources.mapNotNull { dataSource ->
    try {
      resolver.resolve(dataSource).unwrap(HikariDataSource::class.java)
    } catch (_: SQLException) {
      null
    }
  }

  override fun getResult(): Publisher<HealthResult> {
    if (hikariSources.isEmpty()) return Flux.empty()
    val perPool = hikariSources.map(::getResult)
    return healthAggregator.aggregate(NAME, Flux.merge(perPool))
  }

  private fun getResult(hikari: HikariDataSource): Publisher<HealthResult> = Mono.fromCallable {
    // Hikari validates connections internally, so we don't need to perform any explicit action.
    hikari.connection.use { }
    HealthResult.builder(hikari.poolName, HealthStatus.UP)
      .details(poolStats(hikari)).build()
  }
    // subscribeOn() so the callable runs off the subscribing thread — without this,
    // fromCallable executes synchronously and timeout() cannot interrupt a blocked getConnection().
    // Uses Micronaut's blocking executor (same pool as AbstractHealthIndicator and JdbcIndicator)
    // rather than Reactor's boundedElastic() to avoid introducing a second thread pool.
    .subscribeOn(Schedulers.fromExecutorService(blockingExecutor))
    .timeout(Duration.ofSeconds(HEALTH_CHECK_TIMEOUT_SECONDS))
    .onErrorResume { ex ->
      Mono.just(
        HealthResult.builder(hikari.poolName, HealthStatus.DOWN)
          .exception(ex)
          .details(poolStats(hikari)).build(),
      )
    }

  private fun poolStats(hikari: HikariDataSource): Map<String, Any> {
    val mxBean = hikari.hikariPoolMXBean

    return mapOf(
      "pool" to hikari.poolName,
      "totalConnections" to (mxBean?.totalConnections ?: 0),
      "activeConnections" to (mxBean?.activeConnections ?: 0),
      "idleConnections" to (mxBean?.idleConnections ?: 0),
      "threadsAwaitingConnection" to (mxBean?.threadsAwaitingConnection ?: 0),
    )
  }

  private companion object {
    private const val NAME = "hikariCP"

    /**
     * If we're not able to quickly acquire a connection, the pool is overloaded, and we should
     * indicate a health problem.
     */
    private const val HEALTH_CHECK_TIMEOUT_SECONDS = 2L
  }
}
