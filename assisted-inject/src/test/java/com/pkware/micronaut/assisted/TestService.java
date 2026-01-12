package com.pkware.micronaut.assisted;

import jakarta.inject.Singleton;

@Singleton
public class TestService {
    public String process(String input) {
        return "SERVICE:" + input;
    }
}
