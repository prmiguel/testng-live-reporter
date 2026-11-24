package com.acme.tests;

import org.testng.annotations.DataProvider;

public final class SharedProviders {

    @DataProvider(name = "shared")
    public static Object[][] shared() {
        return new Object[][]{{"a"}, {"b"}};
    }
}
