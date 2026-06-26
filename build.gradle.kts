plugins {
    // Anchors GraalVM native plugin in the root classloader so sibling subprojects that
    // get it transitively via io.micronaut.library share one classloader scope instead of
    // each getting their own — prevents GraalVMReachabilityMetadataService type mismatch.
    id("org.graalvm.buildtools.native") version "1.1.3" apply false
}