package com.pkware.micronaut.security.oauth2

import com.nimbusds.oauth2.sdk.TokenRequest
import io.micronaut.testresources.core.TestResourcesResolver
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.OAuth2Config
import no.nav.security.mock.oauth2.token.OAuth2TokenCallback
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

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
 * ### Server-side scope assignment
 *
 * The server assigns scopes to clients based on their `client_id`, mirroring production
 * OAuth2 providers like AWS Cognito that grant registered scopes without requiring the
 * client to request them. Configure the mapping with:
 *
 * ```properties
 * test-resources.mock-oauth2.client-credentials-scopes.<clientId>=<scope1> <scope2>
 * ```
 *
 * For example:
 * ```properties
 * test-resources.mock-oauth2.client-credentials-scopes.00000000-0000-7000-c000-000000000003=data_center:worker platform:service
 * ```
 *
 * When a matching `client_id` is found in the token request, those scopes are placed in the JWT
 * regardless of what the client requested. Clients without a mapping fall back to echoing the
 * requested scope.
 *
 * A single [MockOAuth2Server] instance is shared across all client names. The server starts
 * lazily on first property resolution and remains alive for the test-resources lifecycle.
 * The scope map is updated on each resolve call so that any module's scope configuration
 * is applied before token requests are made.
 */
public class MockOAuth2TestResourceProvider : TestResourcesResolver {

  @Volatile
  private var server: MockOAuth2Server? = null

  /**
   * All client names that have ever been registered with this provider.
   *
   * Used to detect when a new resolve call brings in a client name that was not present when
   * the server was first started. In that case [ensureServerStarted] shuts down and restarts
   * the server on the same port, this time including [ClientCredentialsTokenCallback] instances
   * for every accumulated client name.
   */
  private val registeredClientNames: MutableSet<String> = ConcurrentHashMap.newKeySet()

  /**
   * Shared map of `client_id` → space-separated scope string, updated from each module's
   * test-resources configuration. Shared by reference with all [ClientCredentialsTokenCallback]
   * instances so scope assignments are visible regardless of which module started the server.
   */
  private val clientScopeMap: ConcurrentHashMap<String, String> = ConcurrentHashMap()

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

    // Update scope map from this module's config before using the server.
    // All resolve calls run before any application code makes token requests,
    // so the map is fully populated before the first client credentials grant.
    updateClientScopeMap(testResourcesConfig)

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

  /**
   * Starts the server if not yet running, or restarts it on the same port if new client names
   * have arrived that weren't present at last startup.
   *
   * The test-resources JVM is shared across all Gradle modules. When multiple modules each
   * configure their own `test-resources.mock-oauth2.client-names`, the first module to resolve
   * starts the server. Later modules may bring new names that need their own
   * [ClientCredentialsTokenCallback]. Without a restart those tokens would be handled by
   * [no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback], which omits the `client_id`
   * claim and breaks validators like [recurse.site.security.CognitoTokenValidator].
   *
   * Restarting on the same port preserves already-resolved URLs (issuer, JWKS, token endpoint).
   */
  private fun ensureServerStarted(clientNames: List<String>): MockOAuth2Server {
    // Fast path: server running and all names already registered.
    val currentServer = server
    if (currentServer != null && registeredClientNames.containsAll(clientNames)) {
      return currentServer
    }

    synchronized(this) {
      val newNames = clientNames.filter { it !in registeredClientNames }
      val existingServer = server

      if (newNames.isEmpty() && existingServer != null) {
        return existingServer
      }

      // Record the port before shutdown so we can reuse it — resolved URLs (issuer, JWKS,
      // token endpoint) are already cached by the test context and must not change.
      val port = existingServer?.baseUrl()?.port ?: 0
      existingServer?.shutdown()

      registeredClientNames.addAll(clientNames)

      val allCallbacks = registeredClientNames
        .map { name -> ClientCredentialsTokenCallback(name, clientScopeMap) }
        .toSet()
      val config = OAuth2Config(interactiveLogin = false, tokenCallbacks = allCallbacks)
      val newServer = MockOAuth2Server(config)
      newServer.start(port)
      server = newServer
      return newServer
    }
  }

  /**
   * Reads scope entries from test-resources config and adds them to [clientScopeMap].
   *
   * Reads all properties under the [CLIENT_CREDENTIALS_SCOPES_PREFIX] prefix in the form
   * `mock-oauth2.client-credentials-scopes.<clientId>=<scope>`.
   */
  private fun updateClientScopeMap(testResourcesConfig: Map<String, Any>) {
    val prefix = "$CLIENT_CREDENTIALS_SCOPES_PREFIX."
    for ((key, value) in testResourcesConfig) {
      if (key.startsWith(prefix)) {
        val clientId = key.removePrefix(prefix)
        val scope = value as? String ?: continue
        if (clientId.isNotEmpty() && scope.isNotEmpty()) {
          clientScopeMap[clientId] = scope
        }
      }
    }
  }

  /**
   * Token callback for `client_credentials` grants that assigns scopes server-side based on
   * `client_id`, mirroring production OAuth2 providers like AWS Cognito.
   *
   * Scopes are resolved in priority order:
   * 1. Server-configured scopes from [clientScopes] keyed by `client_id`.
   * 2. Client-requested scopes from the token request (fallback for unconfigured clients).
   *
   * The `client_id` claim is always included so that validators like
   * [recurse.site.security.CognitoTokenValidator] can look up the caller's identity.
   */
  private class ClientCredentialsTokenCallback(private val id: String, private val clientScopes: Map<String, String>) :
    OAuth2TokenCallback {
    override fun issuerId(): String = id
    override fun typeHeader(tokenRequest: TokenRequest): String = "JWT"
    override fun tokenExpiry(): Long = 3600

    override fun subject(tokenRequest: TokenRequest): String = extractClientId(tokenRequest) ?: "unknown"

    override fun audience(tokenRequest: TokenRequest): List<String> = listOf("default")

    override fun addClaims(tokenRequest: TokenRequest): Map<String, Any> = buildMap {
      val clientId = extractClientId(tokenRequest)
      if (clientId != null) put("client_id", clientId)
      // Server-configured scopes take precedence over client-requested scopes,
      // mirroring production behavior where the OAuth2 server assigns scopes based on
      // client registration rather than on what the client requests.
      val scope = clientId?.let { clientScopes[it] } ?: tokenRequest.scope?.toString()
      if (scope != null) put("scope", scope)
    }

    private fun extractClientId(tokenRequest: TokenRequest): String? =
      tokenRequest.clientAuthentication?.clientID?.value
        ?: tokenRequest.clientID?.value
  }

  private companion object {
    private const val CLIENT_NAMES_KEY = "mock-oauth2.client-names"

    /**
     * Prefix for server-side scope assignment config entries.
     *
     * Full property path: `test-resources.mock-oauth2.client-credentials-scopes.<clientId>`.
     * The `test-resources.` prefix is stripped by the test-resources framework before reaching
     * this provider, so entries arrive as `mock-oauth2.client-credentials-scopes.<clientId>`.
     */
    private const val CLIENT_CREDENTIALS_SCOPES_PREFIX = "mock-oauth2.client-credentials-scopes"

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
