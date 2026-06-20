package com.pkware.micronaut.json;

/** A Java record used to probe whether Moshi's reflective adapter handles records on Java 25. */
public record JavaRecordFixture(String name, int count) {}
