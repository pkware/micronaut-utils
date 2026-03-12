package com.pkware.micronaut.security.oauth2

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MockOAuth2TestResourceProviderTest {

  private val provider = MockOAuth2TestResourceProvider()

  @AfterAll
  fun tearDown() {
    // Resolve a property to ensure the server is started, then let GC handle shutdown.
    // The provider doesn't expose a close method — the server shuts down with the JVM.
  }

  @Nested
  inner class GetResolvableProperties {

    @Test
    fun `returns empty when no client names configured`() {
      val result = provider.getResolvableProperties(emptyMap(), emptyMap())
      assertThat(result).isEmpty()
    }

    @Test
    fun `returns properties for single client name`() {
      val config = mapOf("mock-oauth2.client-names" to "backend")
      val result = provider.getResolvableProperties(emptyMap(), config)

      assertThat(result).containsExactlyInAnyOrder(
        "micronaut.security.oauth2.clients.backend.openid.issuer",
        "micronaut.security.oauth2.clients.backend.client-id",
        "micronaut.security.oauth2.clients.backend.client-secret",
      )
    }

    @Test
    fun `returns properties for multiple client names`() {
      val config = mapOf("mock-oauth2.client-names" to "backend,analytics")
      val result = provider.getResolvableProperties(emptyMap(), config)

      assertThat(result).hasSize(6)
      assertThat(result).contains("micronaut.security.oauth2.clients.backend.openid.issuer")
      assertThat(result).contains("micronaut.security.oauth2.clients.analytics.openid.issuer")
      assertThat(result).contains("micronaut.security.oauth2.clients.backend.client-id")
      assertThat(result).contains("micronaut.security.oauth2.clients.analytics.client-id")
      assertThat(result).contains("micronaut.security.oauth2.clients.backend.client-secret")
      assertThat(result).contains("micronaut.security.oauth2.clients.analytics.client-secret")
    }

    @Test
    fun `trims whitespace from client names`() {
      val config = mapOf("mock-oauth2.client-names" to " backend , analytics ")
      val result = provider.getResolvableProperties(emptyMap(), config)

      assertThat(result).contains("micronaut.security.oauth2.clients.backend.openid.issuer")
      assertThat(result).contains("micronaut.security.oauth2.clients.analytics.openid.issuer")
    }

    @Test
    fun `ignores empty segments in client names`() {
      val config = mapOf("mock-oauth2.client-names" to "backend,,analytics")
      val result = provider.getResolvableProperties(emptyMap(), config)

      assertThat(result).hasSize(6)
    }
  }

  @Nested
  inner class Resolve {

    private val config = mapOf("mock-oauth2.client-names" to "backend,analytics")

    @Test
    fun `resolves issuer URL for configured client`() {
      val result = provider.resolve(
        "micronaut.security.oauth2.clients.backend.openid.issuer",
        emptyMap(),
        config,
      )

      assertThat(result.isPresent).isTrue()
      assertThat(result.get()).isNotEmpty()
      assertThat(result.get()).contains("backend")
    }

    @Test
    fun `resolves client-id for configured client`() {
      val result = provider.resolve(
        "micronaut.security.oauth2.clients.backend.client-id",
        emptyMap(),
        config,
      )

      assertThat(result.isPresent).isTrue()
      assertThat(result.get()).isEqualTo("test-client-id-backend")
    }

    @Test
    fun `resolves client-secret for configured client`() {
      val result = provider.resolve(
        "micronaut.security.oauth2.clients.backend.client-secret",
        emptyMap(),
        config,
      )

      assertThat(result.isPresent).isTrue()
      assertThat(result.get()).isEqualTo("test-client-secret-backend")
    }

    @Test
    fun `resolves properties for second client name`() {
      val issuer = provider.resolve(
        "micronaut.security.oauth2.clients.analytics.openid.issuer",
        emptyMap(),
        config,
      )

      assertThat(issuer.isPresent).isTrue()
      assertThat(issuer.get()).contains("analytics")
    }

    @Test
    fun `returns empty for unconfigured client name`() {
      val result = provider.resolve(
        "micronaut.security.oauth2.clients.unknown.openid.issuer",
        emptyMap(),
        config,
      )

      assertThat(result.isPresent).isEqualTo(false)
    }

    @Test
    fun `returns empty for unrecognized property suffix`() {
      val result = provider.resolve(
        "micronaut.security.oauth2.clients.backend.grant-type",
        emptyMap(),
        config,
      )

      assertThat(result.isPresent).isEqualTo(false)
    }

    @Test
    fun `returns empty when no client names configured`() {
      val result = provider.resolve(
        "micronaut.security.oauth2.clients.backend.openid.issuer",
        emptyMap(),
        emptyMap(),
      )

      assertThat(result.isPresent).isEqualTo(false)
    }

    @Test
    fun `returns empty for completely unrelated property`() {
      val result = provider.resolve(
        "datasources.default.url",
        emptyMap(),
        config,
      )

      assertThat(result.isPresent).isEqualTo(false)
    }

    @Test
    fun `different client names produce different issuer URLs`() {
      val backendIssuer = provider.resolve(
        "micronaut.security.oauth2.clients.backend.openid.issuer",
        emptyMap(),
        config,
      )
      val analyticsIssuer = provider.resolve(
        "micronaut.security.oauth2.clients.analytics.openid.issuer",
        emptyMap(),
        config,
      )

      assertThat(backendIssuer.get()).isNotNull()
      assertThat(analyticsIssuer.get()).isNotNull()
      // Both should contain their respective client names in the issuer URL path.
      assertThat(backendIssuer.get()).contains("backend")
      assertThat(analyticsIssuer.get()).contains("analytics")
    }
  }

  @Nested
  inner class ServerLifecycle {

    @Test
    fun `server starts only once across multiple resolve calls`() {
      val config = mapOf("mock-oauth2.client-names" to "one,two")

      val issuer1 = provider.resolve(
        "micronaut.security.oauth2.clients.one.openid.issuer",
        emptyMap(),
        config,
      )
      val issuer2 = provider.resolve(
        "micronaut.security.oauth2.clients.two.openid.issuer",
        emptyMap(),
        config,
      )

      // Both issuers should be on the same host:port (same server instance).
      val host1 = extractHostPort(issuer1.get())
      val host2 = extractHostPort(issuer2.get())
      assertThat(host1).isEqualTo(host2)
    }

    private fun extractHostPort(url: String): String {
      // URL format: http://localhost:<port>/<issuer>
      val uri = java.net.URI.create(url)
      return "${uri.host}:${uri.port}"
    }
  }
}
