package io.github.prmiguel.testng.live.core;

import io.github.prmiguel.testng.live.core.internal.CoreConfiguration;
import io.github.prmiguel.testng.live.core.internal.ExecutionAttempt;
import io.github.prmiguel.testng.live.core.internal.ExecutionEventFactory;
import io.github.prmiguel.testng.live.core.internal.ExecutionTrackerRegistry;
import io.github.prmiguel.testng.live.core.internal.InvocationCounter;
import io.github.prmiguel.testng.live.core.model.ExecutionEvent;
import io.github.prmiguel.testng.live.core.model.ExecutionStatus;
import io.github.prmiguel.testng.live.core.model.FailureInfo;
import io.github.prmiguel.testng.live.core.model.RunInfo;
import io.github.prmiguel.testng.live.core.model.TestIdentity;
import io.github.prmiguel.testng.live.core.spi.ExecutionReporter;
import io.github.prmiguel.testng.live.core.spi.ParameterCanonicalizer;
import org.testng.IExecutionListener;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestNGListener;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Core TestNG listener that publishes live execution events to registered reporters.
 * <p>
 * Reporters are discovered through {@link ServiceLoader} on the classpath. The
 * listener is safe for parallel execution and data-provider invocations.
 */
public class LiveReportingListener implements IExecutionListener, ISuiteListener, ITestListener,
        IInvokedMethodListener, ITestNGListener {

    private static final Logger LOGGER = System.getLogger(LiveReportingListener.class.getName());

    private final CoreConfiguration configuration;
    private final TestRunIdGenerator idGenerator;
    private final List<ExecutionReporter> reporters;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final InvocationCounter invocationCounter = new InvocationCounter();
    private final Set<String> discovered = ConcurrentHashMap.newKeySet();
    private volatile ExecutionEventBus eventBus;
    private volatile ExecutionTrackerRegistry registry;
    private volatile ExecutionEventFactory eventFactory;
    private volatile Instant runStartedAt;

    /**
     * Default constructor used by TestNG and SPI discovery.
     */
    public LiveReportingListener() {
        this(new CoreConfiguration(), discoverCustomCanonicalizers(), discoverReporters());
    }

    /**
     * Constructor for programmatic use and tests.
     */
    public LiveReportingListener(CoreConfiguration configuration,
                                 List<ParameterCanonicalizer> customCanonicalizers,
                                 List<ExecutionReporter> reporters) {
        this.configuration = configuration;
        this.idGenerator = new TestRunIdGenerator(customCanonicalizers);
        this.reporters = List.copyOf(reporters);
    }

    @Override
    public void onExecutionStart() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        this.runStartedAt = Instant.now();
        this.eventBus = new ExecutionEventBus(reporters, configuration);
        this.registry = new ExecutionTrackerRegistry(idGenerator, configuration.runId());
        this.eventFactory = new ExecutionEventFactory(configuration.runId(), eventBus);
        this.eventBus.publishRunStarted(new RunInfo(configuration.runId(), runStartedAt, null));
    }

    @Override
    public void onExecutionFinish() {
        if (eventBus == null) {
            return;
        }
        eventBus.publishRunFinished(new RunInfo(configuration.runId(), runStartedAt, Instant.now()));
        eventBus.close();
    }

    @Override
    public void onStart(ISuite suite) {
        // suite-level events are not emitted
    }

    @Override
    public void onFinish(ISuite suite) {
        // suite-level events are not emitted
    }

    @Override
    public void onStart(ITestContext context) {
        if (eventFactory == null) {
            return;
        }
        String suiteName = context.getSuite() != null ? context.getSuite().getName() : "unknown";
        String testName = context.getName();

        for (ITestNGMethod method : context.getAllTestMethods()) {
            TestIdentity identity = idGenerator.createIdentity(method);
            if (discovered.add(identity.testId())) {
                publish(eventFactory.discovered(identity, suiteName, testName));
            }
        }
        for (ITestNGMethod method : context.getExcludedMethods()) {
            TestIdentity identity = idGenerator.createIdentity(method);
            publish(eventFactory.ignored(identity, suiteName, testName));
        }
    }

    @Override
    public void onFinish(ITestContext context) {
        // flush is handled on execution finish
    }

    @Override
    public void onTestStart(ITestResult result) {
        // STARTED is published from beforeInvocation, which is the only callback that also
        // covers invocations TestNG does not report through onTestStart.
    }

    @Override
    public void beforeInvocation(IInvokedMethod method, ITestResult testResult) {
        if (!method.isTestMethod() || eventFactory == null) {
            return;
        }
        ExecutionAttempt attempt = ensureAttempt(testResult);
        if (attempt.markStarted()) {
            publish(eventFactory.started(attempt));
        }
        if (attempt.markInProgress()) {
            publish(eventFactory.inProgress(attempt));
        }
    }

    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult testResult) {
        // terminal state is emitted by ITestListener result callbacks
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        finish(result, ExecutionStatus.PASSED, null, Collections.emptyMap());
    }

    @Override
    public void onTestFailure(ITestResult result) {
        ExecutionStatus status = classifyFailure(result);
        FailureInfo failure = ExecutionEventFactory.failureInfo(result.getThrowable());
        finish(result, status, failure, Collections.emptyMap());
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        finish(result, ExecutionStatus.SKIPPED, null, Collections.emptyMap());
    }

    @Override
    public void onTestFailedWithTimeout(ITestResult result) {
        FailureInfo failure = ExecutionEventFactory.failureInfo(result.getThrowable());
        finish(result, ExecutionStatus.TIMEOUT, failure, Collections.emptyMap());
    }

    @Override
    public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
        FailureInfo failure = ExecutionEventFactory.failureInfo(result.getThrowable());
        Map<String, String> attributes = new HashMap<>();
        attributes.put("successPercentageFailure", "true");
        finish(result, ExecutionStatus.FAILED, failure, attributes);
    }

    private ExecutionAttempt ensureAttempt(ITestResult result) {
        ExecutionAttempt attempt = registry.get(result);
        if (attempt != null) {
            return attempt;
        }
        TestIdentity identity = idGenerator.createIdentity(result);
        int invocationIndex = invocationCounter.nextIndex(identity.hash());
        int retryAttempt = registry.retryAttempt(identity);
        return registry.begin(result, invocationIndex, retryAttempt);
    }

    private void finish(ITestResult result, ExecutionStatus status, FailureInfo failure, Map<String, String> attributes) {
        if (eventFactory == null) {
            return;
        }
        ExecutionAttempt attempt = ensureAttempt(result);
        if (!attempt.markFinished()) {
            return;
        }
        Map<String, String> resolved = attributes;
        if (result.wasRetried()) {
            Map<String, String> attrs = new HashMap<>(attributes);
            attrs.put("willRetry", "true");
            resolved = Collections.unmodifiableMap(attrs);
            registry.markRetried(attempt.testIdentity());
        }
        publish(eventFactory.finished(attempt, status, failure, resolved,
                result.getStartMillis(), result.getEndMillis()));
        // Evict only after the terminal event is published, so a retry cannot resurrect it.
        registry.complete(result);
    }

    private void publish(ExecutionEvent event) {
        if (eventBus == null) {
            LOGGER.log(Level.WARNING, "Event bus not initialized; dropping event {0}", event.executionId());
            return;
        }
        eventBus.publish(event);
    }

    private ExecutionStatus classifyFailure(ITestResult result) {
        Throwable throwable = result.getThrowable();
        if (throwable == null) {
            return ExecutionStatus.ERROR;
        }
        if (throwable instanceof AssertionError) {
            return ExecutionStatus.FAILED;
        }
        if (throwable instanceof InterruptedException || Thread.currentThread().isInterrupted()) {
            return ExecutionStatus.INTERRUPTED;
        }
        return ExecutionStatus.FAILED;
    }

    private static List<ParameterCanonicalizer> discoverCustomCanonicalizers() {
        List<ParameterCanonicalizer> canonicalizers = new ArrayList<>();
        try {
            ServiceLoader.load(ParameterCanonicalizer.class).forEach(canonicalizers::add);
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Could not load custom ParameterCanonicalizer implementations", e);
        }
        return canonicalizers;
    }

    private static List<ExecutionReporter> discoverReporters() {
        List<ExecutionReporter> reporters = new ArrayList<>();
        try {
            ServiceLoader.load(ExecutionReporter.class).forEach(reporters::add);
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Could not load ExecutionReporter implementations", e);
        }
        return reporters;
    }
}
