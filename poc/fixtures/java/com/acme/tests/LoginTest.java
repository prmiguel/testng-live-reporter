package com.acme.tests;

import com.acme.annotations.TmsId;
import org.testng.Assert;
import org.testng.annotations.Test;

@Test(groups = {"smoke", "ui"})
public class LoginTest extends BaseTest {

    @Test(description = "Valid credentials open the dashboard", groups = {"login"}, priority = 1)
    @TmsId("TMS-101")
    public void validCredentialsOpenDashboard() {
        String page = login("user", "secret");
        Assert.assertEquals(page, "dashboard", "dashboard should open");
    }

    @Test(description = "This description is deliberately far longer than sixty characters so the policy check fires", groups = {"login"})
    @TmsId("TMS-101")
    public void rejectsExpiredPassword() {
        String error = login("user", "expired");
        Assert.assertTrue(error.contains("expired"), "error should mention expiry");
    }

    @Test(enabled = false)
    public void legacyFlow() {
        System.out.println("removed");
    }

    private String login(String user, String password) {
        return "dashboard";
    }
}
