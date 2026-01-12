package com.example.assisted

import jakarta.inject.Singleton

/**
 * A simple DI-managed service that will be injected into products.
 */
@Singleton
class GreetingService {
  fun format(name: String): String = "Hello, $name!"
}
