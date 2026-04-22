package com.pkware.micronaut.security.oauth2

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.nimbusds.jwt.SignedJWT
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MockOAuth2TestResourceProviderTest {

  private val provider = MockOAuth2TestResourceProvider()

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
        "micronaut.security.token.jwt.signatures.jwks.backend.url",
        "mock-oauth2.backend.token-endpoint-url",
      )
    }

    @Test
    fun `returns properties for multiple client names`() {
      val config = mapOf("mock-oauth2.client-names" to "backend,analytics")
      val result = provider.getResolvableProperties(emptyMap(), config)

      assertThat(result).hasSize(10)
      assertThat(result).contains("micronaut.security.oauth2.clients.backend.openid.issuer")
      assertThat(result).contains("micronaut.security.oauth2.clients.analytics.openid.issuer")
      assertThat(result).contains("micronaut.security.token.jwt.signatures.jwks.backend.url")
      assertThat(result).contains("micronaut.security.token.jwt.signatures.jwks.analytics.url")
      assertThat(result).contains("mock-oauth2.backend.token-endpoint-url")
      assertThat(result).contains("mock-oauth2.analytics.token-endpoint-url")
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

      assertThat(result).hasSize(10)
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
    fun `resolves JWKS URL for configured client`() {
      val result = provider.resolve(
        "micronaut.security.token.jwt.signatures.jwks.backend.url",
        emptyMap(),
        config,
      )

      assertThat(result.isPresent).isTrue()
      assertThat(result.get()).contains("backend")
      assertThat(result.get()).contains("jwks")
    }

    @Test
    fun `resolves token endpoint URL for configured client`() {
      val result = provider.resolve(
        "mock-oauth2.backend.token-endpoint-url",
        emptyMap(),
        config,
      )

      assertThat(result.isPresent).isTrue()
      assertThat(result.get()).contains("backend")
      assertThat(result.get()).contains("token")
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
      assertThat(backendIssuer.get()).contains("backend")
      assertThat(analyticsIssuer.get()).contains("analytics")
    }
  }

  @Nested
  inner class TokenEndpoint {

    // Separate provider to avoid interference from other tests' server initialization.
    // The server starts lazily with the first set of client names; a shared provider
    // could start with different names (e.g., from ServerLifecycle) and lack the callback.
    private val tokenProvider = MockOAuth2TestResourceProvider()
    private val config = mapOf("mock-oauth2.client-names" to "backend")
    private val httpClient = OkHttpClient()

    @Test
    fun `client_credentials token includes client_id claim`() {
      val tokenEndpoint = tokenProvider.resolve(
        "mock-oauth2.backend.token-endpoint-url",
        emptyMap(),
        config,
      ).get()

      val jwt = requestToken(tokenEndpoint, clientId = "my-client-id", scope = "read write")
      val claims = SignedJWT.parse(jwt).jwtClaimsSet

      assertThat(claims.getStringClaim("client_id")).isEqualTo("my-client-id")
    }

    @Test
    fun `client_credentials token includes scope claim`() {
      val tokenEndpoint = tokenProvider.resolve(
        "mock-oauth2.backend.token-endpoint-url",
        emptyMap(),
        config,
      ).get()

      val jwt = requestToken(tokenEndpoint, clientId = "my-client-id", scope = "read write")
      val claims = SignedJWT.parse(jwt).jwtClaimsSet

      assertThat(claims.getStringClaim("scope")).isEqualTo("read write")
    }

    @Test
    fun `client_credentials token has client_id as subject`() {
      val tokenEndpoint = tokenProvider.resolve(
        "mock-oauth2.backend.token-endpoint-url",
        emptyMap(),
        config,
      ).get()

      val jwt = requestToken(tokenEndpoint, clientId = "my-client-id", scope = "read")
      val claims = SignedJWT.parse(jwt).jwtClaimsSet

      assertThat(claims.subject).isEqualTo("my-client-id")
    }

    private fun requestToken(tokenEndpoint: String, clientId: String, scope: String): String {
      val body = FormBody.Builder()
        .add("grant_type", "client_credentials")
        .add("client_id", clientId)
        .add("client_secret", "ignored")
        .add("scope", scope)
        .build()

      val request = Request.Builder()
        .url(tokenEndpoint)
        .post(body)
        .build()

      val response = httpClient.newCall(request).execute()
      val json = response.body!!.string()
      // Extract access_token from JSON response: {"access_token":"...","token_type":"Bearer",...}
      val tokenMatch = Regex(""""access_token"\s*:\s*"([^"]+)"""").find(json)
      return tokenMatch!!.groupValues[1]
    }
  }

  @Nested
  inner class ServerSideScopes {

    // Separate provider per test class to avoid shared server state.
    private val scopedProvider = MockOAuth2TestResourceProvider()
    private val httpClient = OkHttpClient()

    private val baseConfig = mapOf("mock-oauth2.client-names" to "cognito")
    private val configWithScopes = baseConfig + mapOf(
      "mock-oauth2.client-credentials-scopes.registered-client" to "service:read service:write",
    )

    @Test
    fun `registered client gets server-assigned scopes without requesting any`() {
      val tokenEndpoint = scopedProvider.resolve(
        "mock-oauth2.cognito.token-endpoint-url",
        emptyMap(),
        configWithScopes,
      ).get()

      val jwt = requestToken(tokenEndpoint, clientId = "registered-client", scope = null)
      val claims = SignedJWT.parse(jwt).jwtClaimsSet

      assertThat(claims.getStringClaim("scope")).isEqualTo("service:read service:write")
    }

    @Test
    fun `server-assigned scopes override any scope the client requests`() {
      val tokenEndpoint = scopedProvider.resolve(
        "mock-oauth2.cognito.token-endpoint-url",
        emptyMap(),
        configWithScopes,
      ).get()

      val jwt = requestToken(tokenEndpoint, clientId = "registered-client", scope = "wrong:scope")
      val claims = SignedJWT.parse(jwt).jwtClaimsSet

      assertThat(claims.getStringClaim("scope")).isEqualTo("service:read service:write")
    }

    @Test
    fun `unregistered client echoes back its requested scope`() {
      val tokenEndpoint = scopedProvider.resolve(
        "mock-oauth2.cognito.token-endpoint-url",
        emptyMap(),
        configWithScopes,
      ).get()

      val jwt = requestToken(tokenEndpoint, clientId = "unknown-client", scope = "arbitrary:scope")
      val claims = SignedJWT.parse(jwt).jwtClaimsSet

      assertThat(claims.getStringClaim("scope")).isEqualTo("arbitrary:scope")
    }

    @Test
    fun `unregistered client with no scope request gets no scope claim`() {
      val tokenEndpoint = scopedProvider.resolve(
        "mock-oauth2.cognito.token-endpoint-url",
        emptyMap(),
        configWithScopes,
      ).get()

      val jwt = requestToken(tokenEndpoint, clientId = "unknown-client", scope = null)
      val claims = SignedJWT.parse(jwt).jwtClaimsSet

      assertThat(claims.getStringClaim("scope")).isNull()
    }

    @Test
    fun `scope map is updated from subsequent resolve calls`() {
      // Start server with no scope config.
      val tokenEndpoint = scopedProvider.resolve(
        "mock-oauth2.cognito.token-endpoint-url",
        emptyMap(),
        baseConfig,
      ).get()

      // A later resolve call from a different module adds the scope mapping.
      scopedProvider.resolve("mock-oauth2.cognito.token-endpoint-url", emptyMap(), configWithScopes)

      // By the time any real token request fires, all resolve calls have completed,
      // so the updated mapping applies.
      val jwt = requestToken(tokenEndpoint, clientId = "registered-client", scope = null)
      val claims = SignedJWT.parse(jwt).jwtClaimsSet

      assertThat(claims.getStringClaim("scope")).isEqualTo("service:read service:write")
    }

    private fun requestToken(tokenEndpoint: String, clientId: String, scope: String?): String {
      val bodyBuilder = FormBody.Builder()
        .add("grant_type", "client_credentials")
        .add("client_id", clientId)
        .add("client_secret", "ignored")
      if (scope != null) bodyBuilder.add("scope", scope)
      val body = bodyBuilder.build()

      val request = Request.Builder().url(tokenEndpoint).post(body).build()
      val response = httpClient.newCall(request).execute()
      val json = response.body!!.string()
      val tokenMatch = Regex(""""access_token"\s*:\s*"([^"]+)"""").find(json)
      return tokenMatch!!.groupValues[1]
    }
  }

  @Nested
  inner class ServerLifecycle {

    @Test
    fun `server starts only once across multiple resolve calls`() {
      val config = mapOf("mock-oauth2.client-names" to "backend,analytics")

      val issuer1 = provider.resolve(
        "micronaut.security.oauth2.clients.backend.openid.issuer",
        emptyMap(),
        config,
      )
      val issuer2 = provider.resolve(
        "micronaut.security.oauth2.clients.analytics.openid.issuer",
        emptyMap(),
        config,
      )

      val host1 = extractHostPort(issuer1.get())
      val host2 = extractHostPort(issuer2.get())
      assertThat(host1).isEqualTo(host2)
    }

    private fun extractHostPort(url: String): String {
      val uri = java.net.URI.create(url)
      return "${uri.host}:${uri.port}"
    }
  }
}
