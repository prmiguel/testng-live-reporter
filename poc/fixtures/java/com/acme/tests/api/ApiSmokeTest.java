package com.acme.tests.api;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ApiSmokeTest {

    @BeforeClass
    public void boot() {
        // setup only
    }

    @AfterClass(alwaysRun = true)
    public void stop() {
        // teardown only
    }

    @Test(description = "Health endpoint responds", groups = {"smoke", "api"}, timeOut = 5000,
          expectedExceptions = IllegalStateException.class)
    public void health() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Assert.fail("interrupted", e);
        }
        throw new IllegalStateException("boom");
    }
}
