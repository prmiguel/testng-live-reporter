package com.acme.tests;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

public abstract class BaseTest {
    protected String tenant;

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        tenant = "acme";
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        tenant = null;
    }
}
