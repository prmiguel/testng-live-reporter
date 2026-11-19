package io.github.prmiguel.testng.live.json.internal;

import io.github.prmiguel.testng.live.core.model.ExecutionEvent;
import io.github.prmiguel.testng.live.json.JsonReporterConfiguration;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Decides whether an event should be written.
 * <p>
 * Non-terminal events are always kept so a live stream never loses the context that
 * explains a later terminal event; status filtering applies to finished events only.
 * {@code DISCOVERED} is non-terminal, so it survives a status whitelist such as
 * {@code FAILED,TIMEOUT}; {@code IGNORED} is terminal and is therefore filtered like any
 * other outcome.
 */
public final class EventFilter {

    private final List<Pattern> includeClasses;
    private final List<Pattern> excludeClasses;
    private final List<Pattern> includeMethods;
    private final List<Pattern> excludeMethods;
    private final List<String> includeStatuses;

    public EventFilter(JsonReporterConfiguration configuration) {
        this.includeClasses = globsToPatterns(configuration.includeClasses());
        this.excludeClasses = globsToPatterns(configuration.excludeClasses());
        this.includeMethods = globsToPatterns(configuration.includeMethods());
        this.excludeMethods = globsToPatterns(configuration.excludeMethods());
        this.includeStatuses = configuration.includeStatuses();
    }

    /**
     * Returns true when the event should be written.
     *
     * @param event candidate event
     * @return true when accepted
     */
    public boolean accepts(ExecutionEvent event) {
        String className = event.testIdentity().className();
        String methodName = event.testIdentity().methodName();

        if (!includeClasses.isEmpty() && !matchesAny(includeClasses, className)) {
            return false;
        }
        if (matchesAny(excludeClasses, className)) {
            return false;
        }
        if (!includeMethods.isEmpty() && !matchesAny(includeMethods, methodName)) {
            return false;
        }
        if (matchesAny(excludeMethods, methodName)) {
            return false;
        }
        if (!event.isTerminal() || includeStatuses.isEmpty()) {
            return true;
        }
        return includeStatuses.contains(event.status().name());
    }

    private static boolean matchesAny(List<Pattern> patterns, String value) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(value).matches()) {
                return true;
            }
        }
        return false;
    }

    private static List<Pattern> globsToPatterns(List<String> globs) {
        return globs.stream().map(EventFilter::globToPattern).toList();
    }

    /**
     * Converts a simple glob ({@code *} and {@code ?}) into an anchored regex.
     */
    static Pattern globToPattern(String glob) {
        StringBuilder regex = new StringBuilder();
        StringBuilder literal = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> {
                    flushLiteral(regex, literal);
                    regex.append(".*");
                }
                case '?' -> {
                    flushLiteral(regex, literal);
                    regex.append('.');
                }
                default -> literal.append(c);
            }
        }
        flushLiteral(regex, literal);
        return Pattern.compile(regex.toString());
    }

    private static void flushLiteral(StringBuilder regex, StringBuilder literal) {
        if (literal.isEmpty()) {
            return;
        }
        regex.append(Pattern.quote(literal.toString()));
        literal.setLength(0);
    }
}
