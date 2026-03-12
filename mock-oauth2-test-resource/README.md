# mock-oauth2-test-resource

A [Micronaut test-resources](https://micronaut-projects.github.io/micronaut-test-resources/latest/guide/) provider that starts an in-process [MockOAuth2Server](https://github.com/navikt/mock-oauth2-server) and feeds OAuth2 client configuration into the Micronaut test context.

## Usage

Add the dependency to your test-resources classpath:

```kotlin
testResourcesService("com.pkware.micronaut:mock-oauth2-test-resource:<version>")
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

A single `MockOAuth2Server` instance is shared across all client names, started on a random port on first use.
