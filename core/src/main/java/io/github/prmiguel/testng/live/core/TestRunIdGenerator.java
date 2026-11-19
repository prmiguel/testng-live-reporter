package io.github.prmiguel.testng.live.core;

import io.github.prmiguel.testng.live.core.internal.DefaultParameterCanonicalizer;
import io.github.prmiguel.testng.live.core.model.CanonicalParameter;
import io.github.prmiguel.testng.live.core.model.TestIdentity;
import io.github.prmiguel.testng.live.core.spi.ParameterCanonicalizer;
import org.testng.IDataProviderMethod;
import org.testng.IRetryAnalyzer;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Generates stable test identities and unique execution identifiers.
 */
public final class TestRunIdGenerator {

    private final List<ParameterCanonicalizer> customCanonicalizers;

    public TestRunIdGenerator(List<ParameterCanonicalizer> customCanonicalizers) {
        this.customCanonicalizers = List.copyOf(customCanonicalizers);
    }

    /**
     * Generates a run identifier.
     *
     * @return a UUID string
     */
    public static String generateRunId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Creates a stable identity for the test represented by the given result.
     *
     * @param result TestNG result
     * @return stable test identity
     */
    public TestIdentity createIdentity(ITestResult result) {
        Objects.requireNonNull(result, "result must not be null");

        String className = result.getMethod().getTestClass().getRealClass().getName();
        String methodName = result.getMethod().getMethodName();

        Class<?>[] declaredTypes = result.getMethod().getConstructorOrMethod().getParameterTypes();
        Object[] values = result.getParameters();

        List<CanonicalParameter> parameters = new ArrayList<>(values.length);
        List<String> canonicalParts = new ArrayList<>(values.length);
        List<String> displayParts = new ArrayList<>(values.length);

        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            String declaredType = i < declaredTypes.length ? declaredTypes[i].getName() : (value == null ? "java.lang.Object" : value.getClass().getName());
            String runtimeType = value == null ? declaredType : value.getClass().getName();
            String canonicalValue = canonicalize(value);

            parameters.add(new CanonicalParameter(declaredType, runtimeType, canonicalValue));
            canonicalParts.add(canonicalValue);
            displayParts.add(displayValue(value));
        }

        String readableKey = className + "." + methodName + "[" + String.join(", ", displayParts) + "]";
        String canonicalPayload = className + "#" + methodName + "#" + String.join("|", canonicalParts);
        String hash = sha256Prefix(canonicalPayload, 16);
        String testId = readableKey + "#" + hash;

        return new TestIdentity(className, methodName, parameters, readableKey, hash, testId,
                annotations(result.getMethod()));
    }

    /**
     * Creates a stable identity for a method that has not been invoked yet.
     * <p>
     * Discovery callbacks are not handed an {@link ITestResult}, so no parameters are
     * known. The hashing payload is identical to {@link #createIdentity(ITestResult)}, so a
     * parameterless method resolves to the same identity before and after it runs.
     *
     * @param method discovered TestNG method
     * @return stable test identity
     */
    public TestIdentity createIdentity(ITestNGMethod method) {
        Objects.requireNonNull(method, "method must not be null");

        String className = method.getTestClass().getRealClass().getName();
        String methodName = method.getMethodName();

        String readableKey = className + "." + methodName + "[]";
        String canonicalPayload = className + "#" + methodName + "#";
        String hash = sha256Prefix(canonicalPayload, 16);
        String testId = readableKey + "#" + hash;

        return new TestIdentity(className, methodName, Collections.emptyList(), readableKey, hash, testId,
                annotations(method));
    }

    /**
     * Reads the {@code @Test} metadata TestNG exposes on {@code ITestNGMethod}.
     * <p>
     * Only directly available getters are used: reflection on the annotation itself would have to
     * reimplement TestNG's class-level merge, and a metadata lookup must never fail a run, so
     * every value is defensive.
     *
     * @param method method to describe, may be null for configuration methods
     * @return captured metadata, never null
     */
    static TestIdentity.Annotations annotations(ITestNGMethod method) {
        if (method == null) {
            return TestIdentity.Annotations.EMPTY;
        }
        TestIdentity.Annotations captured = new TestIdentity.Annotations(
                List.of(safeArray(method::getGroups)),
                List.of(safeArray(method::getGroupsDependedUpon)),
                method.getEnabled() ? null : Boolean.FALSE,
                blankToNull(safe(() -> method.getDescription())),
                zeroToNull(safeInt(method::getPriority)),
                zeroToNull(safeLong(method::getTimeOut)),
                safeInt(method::getInvocationCount) == 1 ? null : safeInt(method::getInvocationCount),
                retryAnalyzerName(method),
                dataProviderName(method));
        return captured.isEmpty() ? TestIdentity.Annotations.EMPTY : captured;
    }

    private static String retryAnalyzerName(ITestNGMethod method) {
        Class<? extends IRetryAnalyzer> analyzer = safe(method::getRetryAnalyzerClass);
        if (analyzer == null) {
            return null;
        }
        // Compared by name: the disabled marker lives in org.testng.internal, and core must not
        // depend on TestNG internals.
        if ("org.testng.internal.annotations.DisabledRetryAnalyzer".equals(analyzer.getName())) {
            return null;
        }
        return analyzer.getName();
    }

    private static String dataProviderName(ITestNGMethod method) {
        try {
            IDataProviderMethod provider = method.getDataProviderMethod();
            return provider == null ? null : blankToNull(provider.getName());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String[] safeArray(Supplier<String[]> supplier) {
        try {
            String[] value = supplier.get();
            return value == null ? new String[0] : value;
        } catch (RuntimeException e) {
            return new String[0];
        }
    }

    private static int safeInt(IntSupplier supplier) {
        try {
            return supplier.getAsInt();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static long safeLong(LongSupplier supplier) {
        try {
            return supplier.getAsLong();
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    private static <T> T safe(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Long zeroToNull(long value) {
        return value == 0L ? null : value;
    }

    private static Integer zeroToNull(int value) {
        return value == 0 ? null : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * Creates a unique execution identifier for a single attempt.
     *
     * @param runId           run identifier
     * @param identity        stable test identity
     * @param invocationIndex invocation index
     * @param retryAttempt    retry attempt number
     * @param threadId        thread id
     * @return execution id
     */
    public String createExecutionId(String runId, TestIdentity identity, int invocationIndex, int retryAttempt, long threadId) {
        return runId + "-" + identity.hash() + "-" + invocationIndex + "-" + retryAttempt + "-" + threadId;
    }

    private String canonicalize(Object value) {
        for (ParameterCanonicalizer canonicalizer : customCanonicalizers) {
            try {
                if (canonicalizer.supports(value)) {
                    return Objects.requireNonNullElse(canonicalizer.canonicalize(value), "null");
                }
            } catch (RuntimeException e) {
                // fall through to default canonicalizer
            }
        }
        return DefaultParameterCanonicalizer.canonicalize(value);
    }

    private String displayValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String s) {
            return "\"" + s + "\"";
        }
        return DefaultParameterCanonicalizer.canonicalize(value);
    }

    private String sha256Prefix(String payload, int length) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < length / 2; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
