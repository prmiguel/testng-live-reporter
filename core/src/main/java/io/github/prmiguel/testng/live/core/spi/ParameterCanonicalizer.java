package io.github.prmiguel.testng.live.core.spi;

/**
 * Strategy for producing a deterministic canonical string representation of a
 * parameter value. Implementations are discovered through
 * {@link java.util.ServiceLoader} and are consulted before the default canonicalizer.
 */
public interface ParameterCanonicalizer {

    /**
     * Returns true if this canonicalizer can handle the given value.
     *
     * @param value the parameter value
     * @return true if supported
     */
    boolean supports(Object value);

    /**
     * Returns a deterministic string representation of the value.
     *
     * @param value the parameter value
     * @return canonical representation
     */
    String canonicalize(Object value);
}
