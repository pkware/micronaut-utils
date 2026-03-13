# mock-oauth2-test-resource

A [Micronaut test-resources](https://micronaut-projects.github.io/micronaut-test-resources/latest/guide/) provider that starts an in-process [MockOAuth2Server](https://github.com/navikt/mock-oauth2-server) and feeds OAuth2 client configuration into the Micronaut test context.

## Usage

Add the dependency to your test-resources classpath:

```kotlin
testResourcesService("com.pkware.micronaut-utils:mock-oauth2-test-resource:<version>")
```

Configure which OAuth2 client names should be provided in your `application-test.properties` (or equivalent):

```properties
test-resources.mock-oauth2.client-names=backend,analytics
```

The provider will resolve the following properties for each client name:

| Property | Value |
|---|---|
| `micronaut.security.oauth2.clients.<name>.openid.issuer` | Mock server issuer URL |
| `micronaut.security.oauth2.clients.<name>.client-id` | `test-client-id-<name>` |
| `micronaut.security.oauth2.clients.<name>.client-secret` | `test-client-secret-<name>` |
| `micronaut.security.token.jwt.signatures.jwks.<name>.url` | JWKS endpoint for JWT signature validation |
| `mock-oauth2.<name>.token-endpoint-url` | Token endpoint for minting tokens via HTTP |

A single `MockOAuth2Server` instance is shared across all client names, started on a random port on first use.

## Token minting

The token endpoint supports `client_credentials` grants and echoes `client_id` and `scope` from the request into JWT claims. This supports services that read these as explicit JWT claim attributes (e.g., for M2M authentication where `client_id` identifies the caller).

```bash
curl -X POST "${mock-oauth2.<name>.token-endpoint-url}" \
  -d "grant_type=client_credentials" \
  -d "client_id=my-service-id" \
  -d "client_secret=ignored" \
  -d "scope=read write"
```

The returned JWT will contain `client_id` and `scope` as claims alongside the standard `sub`, `iss`, and `exp` claims.
