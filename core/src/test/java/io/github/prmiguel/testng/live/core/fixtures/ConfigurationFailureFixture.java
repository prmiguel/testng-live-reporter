package io.github.prmiguel.testng.live.core.fixtures;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Two test methods whose {@code @BeforeMethod} always throws. TestNG reports one
 * configuration failure per method invocation, which is what the reporting test asserts.
 */
public class ConfigurationFailureFixture {

    @BeforeMethod
    public void brokenSetup() {
        throw new IllegalStateException("setup exploded");
    }

    @Test
    public void first() {
        // never runs: the setup fails first
    }

    @Test
    public void second() {
        // never runs: the setup fails first
    }
}
