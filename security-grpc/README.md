# security-grpc

A gRPC `ServerInterceptor` that enforces [Micronaut Security](https://micronaut.io/guides/security/) rules on gRPC service methods. Annotate your gRPC methods with the same `@Secured` annotations you use on HTTP controllers, and access control is handled automatically.

## Security model: deny-by-default

**Every gRPC method call is denied unless a security rule explicitly allows it.** If no rule matches — no `@Secured` annotation and no `intercept-url-map` pattern — the call is rejected with `PERMISSION_DENIED: Method not allowed`.

## Installation

Add both the runtime library and the annotation processor to your build:

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.pkware.micronaut-utils:security-grpc:<version>")
    annotationProcessor("com.pkware.micronaut-utils:security-grpc-processor:<version>") // Java
    // ksp("com.pkware.micronaut-utils:security-grpc-processor:<version>")              // Kotlin KSP
}
```

The annotation processor is required — it generates the `@Executable` metadata that the interceptor needs to read `@Secured` annotations at runtime.

## Usage

### Option 1: `@Secured` annotations

Apply `@Secured` directly to your gRPC service methods:

```java
@Singleton
public class GreeterService implements BindableService {

    @Secured(SecurityRule.IS_ANONYMOUS)       // No authentication required
    public void health(Empty req, StreamObserver<HealthResponse> resp) { ... }

    @Secured(SecurityRule.IS_AUTHENTICATED)   // Any valid token accepted
    public void status(Empty req, StreamObserver<StatusResponse> resp) { ... }

    @Secured("data_center:worker")            // Specific role required
    public void processJob(JobRequest req, StreamObserver<JobResponse> resp) { ... }

    // No @Secured → rejected with PERMISSION_DENIED "Method not allowed"

    @Override
    public ServerServiceDefinition bindService() { ... }
}
```

### Option 2: `intercept-url-map` configuration

For third-party services or when you prefer configuration over annotations, use Micronaut Security's `intercept-url-map`. Patterns use the gRPC full method name prefixed with `/`:

```yaml
micronaut:
  security:
    intercept-url-map:
      - pattern: "/grpc.health.v1.Health/*"
        access:
          - "isAnonymous()"
      - pattern: "/myapp.UserService/GetProfile"
        access:
          - "isAuthenticated()"
      - pattern: "/myapp.AdminService/*"
        access:
          - "role:admin"
```

Both approaches can be used together. Custom `SecurityRule` beans apply to gRPC calls the same way they apply to HTTP requests.

## Accessing the authenticated user

The authenticated `Authentication` is stored in the gRPC `Context` and can be retrieved anywhere downstream:

```java
Authentication auth = GrpcSecurityContext.AUTHENTICATION.get();
String username = auth.getName();
Collection<String> roles = auth.getRoles();
```

## Authorization outcomes

| Condition | gRPC status |
|---|---|
| Rule returns `ALLOWED` | Call proceeds |
| No rule matches the method | `PERMISSION_DENIED: Method not allowed` |
| Rule returns `REJECTED`, no authentication present | `UNAUTHENTICATED` |
| Rule returns `REJECTED`, authenticated but wrong role | `PERMISSION_DENIED: Insufficient scope` |

## How it works

The interceptor runs at application startup and scans all `BindableService` beans for `@Secured` annotations (via the companion annotation processor). On each incoming call it:

1. Looks up the `@Secured` metadata for the called method
2. Builds a synthetic `HttpRequest` from the gRPC `Metadata` headers — this allows the standard Micronaut Security authentication fetchers (Bearer tokens, cookies, etc.) to work without modification
3. Runs the full Micronaut Security authentication and authorization pipeline
4. Stores the resulting `Authentication` in the gRPC `Context` if the call is allowed
5. Records Micrometer metrics under `grpc.security.auth.attempts` (counter) and `grpc.security.authentication` (timer)
