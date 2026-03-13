package com.pkware.micronaut.security.oauth2

import com.nimbusds.oauth2.sdk.TokenRequest
import io.micronaut.testresources.core.TestResourcesResolver
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.OAuth2Config
import no.nav.security.mock.oauth2.token.OAuth2TokenCallback
import java.util.Optional

/**
 * Micronaut test-resources provider that starts an in-process [MockOAuth2Server] and provides
 * OAuth2 client configuration properties to the test context.
 *
 * For each configured client name, the following properties are resolved:
 * - `micronaut.security.oauth2.clients.<name>.openid.issuer`
 * - `micronaut.security.oauth2.clients.<name>.client-id`
 * - `micronaut.security.oauth2.clients.<name>.client-secret`
 * - `micronaut.security.token.jwt.signatures.jwks.<name>.url`
 * - `mock-oauth2.<name>.token-endpoint-url`
 *
 * Configure client names via the test-resources configuration property:
 * ```properties
 * test-resources.mock-oauth2.client-names=backend,analytics
 * ```
 *
 * The server's token endpoint for `client_credentials` grants echoes `client_id` and `scope`
 * from the request into JWT claims. This supports services that read these as explicit JWT
 * claim attributes rather than relying on the standard `sub` claim.
 *
 * A single [MockOAuth2Server] instance is shared across all client names. The server starts
 * lazily on first property resolution and remains alive for the test-resources lifecycle.
 */
public class MockOAuth2TestResourceProvider : TestResourcesResolver {

  @Volatile
  private var server: MockOAuth2Server? = null

  override fun getDisplayName(): String = "Mock OAuth2 Server"

  override fun getResolvableProperties(
    propertyEntries: Map<String, Collection<String>>,
    testResourcesConfig: Map<String, Any>,
  ): List<String> {
    val clientNames = resolveClientNames(testResourcesConfig)
    return clientNames.flatMap { name -> propertiesForClient(name) }
  }

  override fun resolve(
    propertyName: String,
    properties: Map<String, Any>,
    testResourcesConfig: Map<String, Any>,
  ): Optional<String> {
    val clientNames = resolveClientNames(testResourcesConfig)
    if (clientNames.isEmpty()) return Optional.empty()

    val runningServer = ensureServerStarted(clientNames)

    for (name in clientNames) {
      val result = resolveForClient(propertyName, name, runningServer)
      if (result.isPresent) return result
    }
    return Optional.empty()
  }

  private fun resolveForClient(propertyName: String, clientName: String, server: MockOAuth2Server): Optional<String> =
    when (propertyName) {
      "micronaut.security.oauth2.clients.$clientName.openid.issuer" ->
        Optional.of(server.issuerUrl(clientName).toString())

      "micronaut.security.oauth2.clients.$clientName.client-id" ->
        Optional.of("test-client-id-$clientName")

      "micronaut.security.oauth2.clients.$clientName.client-secret" ->
        Optional.of("test-client-secret-$clientName")

      "micronaut.security.token.jwt.signatures.jwks.$clientName.url" ->
        Optional.of(server.jwksUrl(clientName).toString())

      "mock-oauth2.$clientName.token-endpoint-url" ->
        Optional.of(server.tokenEndpointUrl(clientName).toString())

      else -> Optional.empty()
    }

  private fun ensureServerStarted(clientNames: List<String>): MockOAuth2Server {
    server?.let { return it }
    synchronized(this) {
      server?.let { return it }
      val callbacks = clientNames.map { name -> ClientCredentialsTokenCallback(name) }.toSet()
      val config = OAuth2Config(
        interactiveLogin = false,
        tokenCallbacks = callbacks,
      )
      val newServer = MockOAuth2Server(config)
      newServer.start()
      server = newServer
      return newServer
    }
  }

  /**
   * Token callback for `client_credentials` grants that echoes `client_id` and `scope`
   * from the token request into JWT claims.
   *
   * The default [no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback] only sets
   * `sub` to the client ID. Most real OAuth2 providers (AWS Cognito, Auth0, Okta) include
   * `client_id` and `scope` as explicit claims in access tokens, so we match that behavior.
   */
  private class ClientCredentialsTokenCallback(private val id: String) : OAuth2TokenCallback {
    override fun issuerId(): String = id
    override fun typeHeader(tokenRequest: TokenRequest): String = "JWT"
    override fun tokenExpiry(): Long = 3600

    override fun subject(tokenRequest: TokenRequest): String = extractClientId(tokenRequest) ?: "unknown"

    override fun audience(tokenRequest: TokenRequest): List<String> = listOf("default")

    override fun addClaims(tokenRequest: TokenRequest): Map<String, Any> = buildMap {
      extractClientId(tokenRequest)?.let { put("client_id", it) }
      tokenRequest.scope?.toString()?.let { put("scope", it) }
    }

    private fun extractClientId(tokenRequest: TokenRequest): String? =
      tokenRequest.clientAuthentication?.clientID?.value
        ?: tokenRequest.clientID?.value
  }

  private companion object {
    private const val CLIENT_NAMES_KEY = "mock-oauth2.client-names"

    fun resolveClientNames(testResourcesConfig: Map<String, Any>): List<String> {
      val raw = testResourcesConfig[CLIENT_NAMES_KEY] as? String ?: return emptyList()
      return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun propertiesForClient(clientName: String): List<String> = listOf(
      "micronaut.security.oauth2.clients.$clientName.openid.issuer",
      "micronaut.security.oauth2.clients.$clientName.client-id",
      "micronaut.security.oauth2.clients.$clientName.client-secret",
      "micronaut.security.token.jwt.signatures.jwks.$clientName.url",
      "mock-oauth2.$clientName.token-endpoint-url",
    )
  }
}
