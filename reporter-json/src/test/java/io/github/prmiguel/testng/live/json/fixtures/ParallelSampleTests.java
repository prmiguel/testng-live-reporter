package io.github.prmiguel.testng.live.json.fixtures;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Fixture suite for the JSON reporter integration test: one of each status, a data
 * provider with two rows, and a failing test that is retried once.
 */
public class ParallelSampleTests {

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

    @Test(retryAnalyzer = OnceRetryAnalyzer.class)
    public void flakyTest() {
        Assert.assertTrue(OnceRetryAnalyzer.hasRetried(), "should be retried once");
    }
}
