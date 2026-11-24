package com.acme.tests;

import org.testng.Assert;
import org.testng.annotations.BeforeGroups;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

public class PaymentDataProviderTest {

    @DataProvider(name = "cards", parallel = true)
    public static Object[][] cards() {
        return new Object[][]{
                {"visa", "ok"},
                {"amex", "declined"}
        };
    }

    @BeforeGroups("payments")
    public void beforePayments() {
        tenantHint();
    }

    @Test(dataProvider = "cards", description = "Card provider drives settlement", groups = {"payments"})
    public void settlesCard(String card, String expected) {
        Assert.assertEquals(settle(card), expected);
    }

    @Test(dataProvider = "shared", dataProviderClass = SharedProviders.class,
          description = "Shared provider resolves", groups = {"payments"})
    public void sharedProvider(String value) {
        Assert.assertNotNull(value);
    }

    @Test(description = "Parameters come from the suite", groups = {"payments"})
    @Parameters({"env", "browser"})
    public void readsSuiteParameters() {
        Assert.assertTrue(System.getProperty("env") != null, "env should be set");
    }

    @Test(dataProvider = "missing", description = "Provider name is not declared", groups = {"payments"})
    public void usesMissingProvider(String value) {
        Assert.assertNotNull(value);
    }

    @Test(description = "Repeated invocation", groups = {"payments"},
          invocationCount = 2, threadPoolSize = 2, successPercentage = 100,
          singleThreaded = true)
    public void repeatedInvocation() {
        Assert.assertTrue(true);
    }

    private String settle(String card) {
        return card.equals("visa") ? "ok" : "declined";
    }

    private void tenantHint() {
        // no-op
    }
}
