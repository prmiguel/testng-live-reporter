package io.github.prmiguel.testng.live.core.internal;

import io.github.prmiguel.testng.live.core.TestRunIdGenerator;
import io.github.prmiguel.testng.live.core.fixtures.SampleTests;
import io.github.prmiguel.testng.live.core.model.TestIdentity;
import org.junit.jupiter.api.Test;
import org.testng.ITestResult;
import org.testng.TestNG;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ExecutionTrackerRegistryTest {

    private static final TestIdentity IDENTITY = new TestIdentity("com.acme.Sample", "runs", List.of(),
            "com.acme.Sample.runs[]", "0123456789abcdef", "com.acme.Sample.runs[]#0123456789abcdef");

    @Test
    void retryAttemptsStartAtZeroAndIncrease() {
        ExecutionTrackerRegistry registry = new ExecutionTrackerRegistry(
                new TestRunIdGenerator(List.of()), "run-1");

        assertEquals(0, registry.retryAttempt(IDENTITY), "first attempt is 0");

        registry.markRetried(IDENTITY);
        assertEquals(1, registry.retryAttempt(IDENTITY));

        registry.markRetried(IDENTITY);
        assertEquals(2, registry.retryAttempt(IDENTITY));
    }

    @Test
    void evictsAttemptsWhenCompleted() throws Exception {
        ExecutionTrackerRegistry registry = new ExecutionTrackerRegistry(
                new TestRunIdGenerator(List.of()), "run-1");
        ITestResult result = runAndCaptureResult();

        ExecutionAttempt attempt = registry.begin(result, 0, 0);
        assertNotNull(attempt);
        assertEquals(1, registry.inFlight());
        assertEquals(attempt, registry.get(result));

        registry.complete(result);
        assertEquals(0, registry.inFlight(), "completed attempts must not be retained");
        assertNull(registry.get(result), "the TestNG attribute must be cleared too");
    }

    @Test
    void capturesSuiteAndTestNamesOnTheAttempt() throws Exception {
        ExecutionTrackerRegistry registry = new ExecutionTrackerRegistry(
                new TestRunIdGenerator(List.of()), "run-1");
        ITestResult result = runAndCaptureResult();

        ExecutionAttempt attempt = registry.begin(result, 0, 0);
        assertEquals(result.getTestContext().getSuite().getName(), attempt.suiteName());
        assertEquals(result.getTestContext().getName(), attempt.testName());
    }

    private static ITestResult runAndCaptureResult() {
        final ITestResult[] captured = new ITestResult[1];
        TestNG testNG = new TestNG();
        testNG.setTestClasses(new Class[]{SampleTests.class});
        testNG.setUseDefaultListeners(false);
        testNG.addListener(new org.testng.ITestListener() {
            @Override
            public void onTestStart(ITestResult result) {
                captured[0] = result;
            }
        });
        testNG.run();
        return captured[0];
    }
}
