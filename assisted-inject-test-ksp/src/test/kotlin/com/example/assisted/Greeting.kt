package com.example.assisted

import io.micronaut.context.annotation.Parameter
import io.micronaut.context.annotation.Prototype

/**
 * A simple product class that combines an injected service with a runtime parameter.
 */
@Prototype
class Greeting(private val service: GreetingService, @param:Parameter private val name: String) {
  fun greet(): String = service.format(name)
}
