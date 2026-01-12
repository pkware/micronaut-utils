package com.example.assisted;

import com.pkware.micronaut.assisted.Assisted;
import io.micronaut.aop.Introduction;

/**
 * Factory interface that uses @Assisted from the assisted-inject JAR.
 * <p>
 * Note: @Introduction must be added directly because Java annotation processing
 * doesn't process meta-annotations from compiled JARs. This is the exact issue
 * this integration test is designed to catch.
 */
@Introduction
@Assisted
public interface GreetingFactory {
  Greeting create(String name);
}
