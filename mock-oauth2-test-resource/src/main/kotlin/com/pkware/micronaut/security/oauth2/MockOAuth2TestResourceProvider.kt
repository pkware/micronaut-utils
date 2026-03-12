package com.pkware.micronaut.security.oauth2

import io.micronaut.testresources.core.TestResourcesResolver
import no.nav.security.mock.oauth2.MockOAuth2Server
import java.util.Optional

/**
 * Micronaut test-resources provider that starts an in-process [MockOAuth2Server] and provides
 * OAuth2 client configuration properties to the test context.
 *
 * For each configured client name, the following properties are resolved:
 * - `micronaut.security.oauth2.clients.<name>.openid.issuer`
 * - `micronaut.security.oauth2.clients.<name>.client-id`
 * - `micronaut.security.oauth2.clients.<name>.client-secret`
 *
 * Configure client names via the test-resources configuration property:
 * ```properties
 * test-resources.mock-oauth2.client-names=backend,analytics
 * ```
 *
 * A single [MockOAuth2Server] instance is shared across all client names. The server starts
 * lazily on first property resolution and remains alive for the test-resources lifecycle.
 */
class MockOAuth2TestResourceProvider : TestResourcesResolver {

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

    val match = clientNames.firstNotNullOfOrNull { name ->
      val prefix = "micronaut.security.oauth2.clients.$name."
      if (propertyName.startsWith(prefix)) {
        name to propertyName.removePrefix(prefix)
      } else {
        null
      }
    } ?: return Optional.empty()

    val (clientName, suffix) = match
    val runningServer = ensureServerStarted()

    return when (suffix) {
      "openid.issuer" -> Optional.of(runningServer.issuerUrl(clientName).toString())
      "client-id" -> Optional.of("test-client-id-$clientName")
      "client-secret" -> Optional.of("test-client-secret-$clientName")
      else -> Optional.empty()
    }
  }

  private fun ensureServerStarted(): MockOAuth2Server {
    server?.let { return it }
    synchronized(this) {
      server?.let { return it }
      val newServer = MockOAuth2Server()
      newServer.start()
      server = newServer
      return newServer
    }
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
    )
  }
}
