package com.example.assisted;

import jakarta.inject.Singleton;

/**
 * A simple DI-managed service that will be injected into products.
 */
@Singleton
public class GreetingService {
  public String format(String name) {
    return "Hello, " + name + "!";
  }
}
