# security-grpc-processor

Compile-time annotation processor that generates `@Executable` metadata for gRPC service methods, so
downstream authorization can read required scopes from Micronaut's build-time metadata without
`java.lang.reflect`.

It ships two `io.micronaut.inject.annotation.NamedAnnotationMapper`s, registered via
`META-INF/services/io.micronaut.inject.annotation.AnnotationMapper`. Both map their annotation to
`io.micronaut.context.annotation.Executable` and leave the original annotation in place:

| Mapper | Maps | Serves |
| --- | --- | --- |
| `SecuredAnnotationMapper` | `io.micronaut.security.annotation.Secured` | `security-grpc` |
| `RolesAllowedAnnotationMapper` | `jakarta.annotation.security.RolesAllowed` | `grpc-authorization` |

A `NamedAnnotationMapper` handles a single annotation, so each is its own class on its own line in
the SPI file. The mappers use string annotation names, so this module has no compile-time dependency
on `micronaut-security` or the jakarta annotations.

## Usage

Put this processor on the consuming module's annotation processor classpath:

```kotlin
annotationProcessor(projects.securityGrpcProcessor) // or testAnnotationProcessor(...) for tests
```
