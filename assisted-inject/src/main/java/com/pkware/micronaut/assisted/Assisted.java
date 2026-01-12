package com.pkware.micronaut.assisted;

import io.micronaut.aop.Introduction;
import io.micronaut.context.annotation.Parameter;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Enables the <em>Assisted Injection</em> pattern for Micronaut, allowing factories
 * to create objects that combine runtime parameters with dependency-injected services.
 *
 * <h2>Overview</h2>
 * <p>Assisted injection solves the problem of creating objects that require both:
 * <ul>
 *   <li>Dependencies managed by the DI container (e.g., services, mappers)</li>
 *   <li>Runtime values only known at the call site (e.g., user input, configuration)</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <p>Apply this annotation to an interface that declares factory methods. The interface
 * is automatically implemented at compile time via Micronaut's {@link Introduction} mechanism.
 *
 * <h3>Step 1: Define the target class</h3>
 * <p>Mark the class with {@link io.micronaut.context.annotation.Prototype @Prototype}
 * and runtime parameters with {@link Parameter @Parameter}:
 * <p>
 * {@snippet :
 * @Prototype
 * public class MessageFormatter {
 *     private final JsonMapper mapper;      // Injected by Micronaut
 *     private final String template;        // Provided at runtime
 *
 *     public MessageFormatter(JsonMapper mapper, @Parameter String template) {
 *         this.mapper = mapper;
 *         this.template = template;
 *     }
 *
 *     public String format(Object data) {
 *         return template.formatted(mapper.writeValueAsString(data));
 *     }
 * }
 * }
 *
 * <h3>Step 2: Define the factory interface</h3>
 * <p>Annotate the factory with {@code @Assisted}. Factory method parameters map
 * to {@code @Parameter}-annotated constructor parameters by position:
 * <p>
 * {@snippet :
 * @Assisted
 * public interface MessageFormatterFactory {
 *     MessageFormatter create(String template);  // Maps to @Parameter String template
 * }
 * }
 *
 * <h3>Step 3: Inject and use the factory</h3>
 *
 * {@snippet :
 * @Controller("/messages")
 * public class MessageController {
 *     private final MessageFormatterFactory factory;
 *
 *     public MessageController(MessageFormatterFactory factory) {
 *         this.factory = factory;
 *     }
 *
 *     @Get("/{format}")
 *     public String format(String format, @Body Object data) {
 *         // Factory provides 'format' at runtime; JsonMapper is auto-injected
 *         MessageFormatter formatter = factory.create(format);
 *         return formatter.format(data);
 *     }
 * }
 * }
 *
 * <h2>Parameter Matching</h2>
 * <p>Factory method parameters are matched to {@code @Parameter}-annotated constructor
 * parameters by <strong>position</strong>, not by name. Ensure the order matches:
 * <p>
 * {@snippet :
 * // Constructor: (Service service, @Parameter String first, @Parameter int second)
 * // Factory:     create(String first, int second)  ✓ Correct order
 * // Factory:     create(int second, String first)  ✗ Wrong order
 * }
 *
 * <h2>Important Notes</h2>
 * <ul>
 *   <li><strong>Target classes must be beans:</strong> Classes created by the factory must be
 *       annotated with {@link io.micronaut.context.annotation.Prototype @Prototype} (or another
 *       scope annotation) so that {@code BeanContext.createBean()} can instantiate them.</li>
 *   <li><strong>Java annotation processing:</strong> When using Java (not Kotlin KSP), the
 *       {@code @Introduction} meta-annotation may not be processed from compiled JARs. If factory
 *       injection fails, add {@code @Introduction} directly to your factory interface.
 *       Kotlin KSP handles meta-annotations correctly and does not require this workaround.</li>
 * </ul>
 *
 * @see Parameter
 * @see Introduction
 * @see AssistedInterceptor
 */
@Introduction
@Documented
@Target(TYPE)
@Retention(RUNTIME)
public @interface Assisted {
}