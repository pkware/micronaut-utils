package com.example.assisted;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;

/**
 * A simple product class that combines an injected service with a runtime parameter.
 */
@Prototype
public class Greeting {
  private final GreetingService service;
  private final String name;

  public Greeting(GreetingService service, @Parameter String name) {
    this.service = service;
    this.name = name;
  }

  public String greet() {
    return service.format(name);
  }
}
