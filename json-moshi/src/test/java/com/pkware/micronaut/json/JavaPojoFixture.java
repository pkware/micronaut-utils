package com.pkware.micronaut.json;

/** A mutable plain Java POJO used to probe Moshi's reflective ClassJsonAdapter on Java 25. */
public class JavaPojoFixture {
  private String name;
  private int count;

  public JavaPojoFixture() {}

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getCount() {
    return count;
  }

  public void setCount(int count) {
    this.count = count;
  }
}
