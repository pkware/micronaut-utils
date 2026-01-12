# Micronaut Assisted Injection

A lightweight library that brings the **Assisted Injection** pattern to Micronaut, enabling you to create objects that
combine dependency-injected services with runtime parameters.

## The Problem

Sometimes you need to create objects that require both:

- **Injected dependencies** — services, mappers, repositories managed by Micronaut
- **Runtime values** — user input, request parameters, configuration only known at call time

Standard dependency injection can't help here. You end up with awkward workarounds:

```java
// Without assisted injection — manual wiring everywhere
public class OrderController {
    private final JsonMapper mapper;
    private final OrderRepository repository;

    @Post("/orders")
    public Order create(@Body OrderRequest request) {
        // Manually passing dependencies to every new instance
        var processor = new OrderProcessor(mapper, repository, request.customerId());
        return processor.process();
    }
}
```

## The Solution

With assisted injection, define a factory interface and let Micronaut implement it:

```java
@Assisted
public interface OrderProcessorFactory {
    OrderProcessor create(String customerId);
}

public class OrderController {
    private final OrderProcessorFactory factory;

    @Post("/orders")
    public Order create(@Body OrderRequest request) {
        // Factory handles all the wiring
        return factory.create(request.customerId()).process();
    }
}
```

## Installation

Add the dependency to your build:

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.pkware.micronaut:assisted-inject:1.0.0")
}
```

## Quick Start

### 1. Define your class with mixed dependencies

Mark the class with `@Prototype` and use `@Parameter` on constructor parameters that come from the factory:

```java
@Prototype
public class ReportGenerator {
    private final PdfRenderer renderer;    // Injected by Micronaut
    private final DataSource dataSource;   // Injected by Micronaut
    private final String reportType;       // Provided at runtime
    private final LocalDate startDate;     // Provided at runtime

    public ReportGenerator(
            PdfRenderer renderer,
            DataSource dataSource,
            @Parameter String reportType,
            @Parameter LocalDate startDate) {
        this.renderer = renderer;
        this.dataSource = dataSource;
        this.reportType = reportType;
        this.startDate = startDate;
    }

    public byte[] generate() {
        var data = dataSource.query(reportType, startDate);
        return renderer.render(data);
    }
}
```

### 2. Create a factory interface

Annotate with `@Assisted`. Factory method parameters map **positionally** to `@Parameter` constructor args:

```java
@Assisted
public interface ReportGeneratorFactory {
    ReportGenerator create(String reportType, LocalDate startDate);
}
```

### 3. Inject and use the factory

```java
@Controller("/reports")
public class ReportController {
    private final ReportGeneratorFactory factory;

    public ReportController(ReportGeneratorFactory factory) {
        this.factory = factory;
    }

    @Get("/{type}")
    public byte[] generate(String type, @QueryValue LocalDate since) {
        return factory.create(type, since).generate();
    }
}
```

## How It Works

The library uses Micronaut's `@Introduction` AOP mechanism to implement your factory interface at compile time. When a factory method is called:

1. The interceptor captures the method's return type and arguments
2. It delegates to `BeanContext.createBean(returnType, arguments)`
3. Micronaut constructs the object, injecting managed dependencies and using provided arguments for `@Parameter` slots

```
┌─────────────────────────────────────────────────────────────┐
│  factory.create("sales", LocalDate.now())                   │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│  AssistedInterceptor                                        │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ returnType = ReportGenerator.class                    │  │
│  │ args = ["sales", 2024-01-15]                          │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│  BeanContext.createBean(ReportGenerator.class, args)        │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ PdfRenderer    ← injected from context                │  │
│  │ DataSource     ← injected from context                │  │
│  │ reportType     ← "sales" (from args[0])               │  │
│  │ startDate      ← 2024-01-15 (from args[1])            │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

## Parameter Matching

Factory parameters map to `@Parameter` constructor parameters **by position**, not by name:

```java
// Target class
@Prototype
public MyClass(
    Service service,           // position 0 — injected
    @Parameter String first,   // position 0 of @Parameter args
    @Parameter int second      // position 1 of @Parameter args
)

// Factory — parameter order must match @Parameter order
MyClass create(String first, int second);  // ✓ Correct
MyClass create(int second, String first);  // ✗ Wrong — types might match but semantics won't
```

## Multiple Factory Methods

A single factory interface can have multiple methods for different construction scenarios:

```java
@Assisted
public interface NotificationFactory {
    EmailNotification email(String recipient, String subject);
    SmsNotification sms(String phoneNumber);
    PushNotification push(String deviceToken, Map<String, String> data);
}
```

## Kotlin Support

Works seamlessly with Kotlin:

```kotlin
@Prototype
class OrderProcessor(
    private val repository: OrderRepository,  // Injected
    @param:Parameter private val orderId: String  // Runtime
) {
    fun process(): Order = repository.findById(orderId).process()
}

@Assisted
interface OrderProcessorFactory {
    fun create(orderId: String): OrderProcessor
}
```

## Comparison with Other Approaches

| Approach | Boilerplate | Type Safety | Testability |
|----------|-------------|-------------|-------------|
| Manual wiring | High | Compile-time | Hard to mock |
| Provider injection | Medium | Compile-time | Medium |
| **Assisted Injection** | **Low** | **Compile-time** | **Easy** |
| Reflection-based | Low | Runtime only | Easy |

## Testing

Factory interfaces are easy to mock:

```java
@Test
void processesOrder() {
    var mockProcessor = mock(OrderProcessor.class);
    var factory = mock(OrderProcessorFactory.class);
    when(factory.create("order-123")).thenReturn(mockProcessor);

    var controller = new OrderController(factory);
    controller.process("order-123");

    verify(mockProcessor).process();
}
```

## Requirements

- Micronaut 4.x
- Java 21+

## Troubleshooting

### "No bean of type [MyProduct] exists"

Target classes must be annotated with `@Prototype` (or another bean scope) so `BeanContext.createBean()` can instantiate them:

```java
@Prototype  // Required!
public class MyProduct {
    public MyProduct(@Parameter String value) { ... }
}
```

### Factory not generated (Java annotation processing only)

When using **Java annotation processing** (not Kotlin KSP), the `@Introduction` meta-annotation on `@Assisted` may not be processed from compiled JARs. Add `@Introduction` directly to your factory interface:

```java
@Introduction  // Required for Java annotation processing
@Assisted
public interface MyFactory {
    MyProduct create(String value);
}
```

> **Note:** Kotlin projects using KSP do not need this workaround — KSP handles meta-annotations from JARs correctly.
