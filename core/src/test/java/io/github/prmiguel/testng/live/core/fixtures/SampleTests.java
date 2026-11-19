package io.github.prmiguel.testng.live.core.fixtures;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class SampleTests {

    @Test
    public void passingTest() {
        Assert.assertTrue(true);
    }

    @Test
    public void failingTest() {
        Assert.assertTrue(false, "expected failure");
    }

    @Test(dependsOnMethods = "failingTest")
    public void skippedTest() {
        // skipped because failingTest failed
    }

    @Test(timeOut = 100)
    public void timeoutTest() throws InterruptedException {
        Thread.sleep(5000);
    }

    @DataProvider(name = "values")
    public Object[][] values() {
        return new Object[][]{{1}, {2}};
    }

    @Test(dataProvider = "values")
    public void dataProviderTest(int value) {
        Assert.assertTrue(value > 0);
    }

    @Test(groups = "excluded")
    public void excludedTest() {
        // never runs when the excluded group is excluded by the suite
    }
}
