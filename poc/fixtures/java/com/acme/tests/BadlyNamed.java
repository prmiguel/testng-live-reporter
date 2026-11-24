package com.acme.tests;

import com.acme.annotations.TmsId;
import org.testng.Assert;
import org.testng.annotations.Test;

public class BadlyNamed {

    @Test
    public void noAssertion() {
        helper();
    }

    @Test(description = "Typo in the group name", groups = {"payment"})
    @TmsId("TMS-ABC")
    public void typoGroup() {
        Assert.assertTrue(true);
    }

    private void helper() {
        // no-op
    }
}
