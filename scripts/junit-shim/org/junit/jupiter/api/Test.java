package org.junit.jupiter.api;

import java.lang.annotation.*;

/** Local stand-in for JUnit 5's @Test, only used by scripts/run-domain-tests.sh where Maven Central is unreachable. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Test {}
