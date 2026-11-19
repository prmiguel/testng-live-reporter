package io.github.prmiguel.testng.live.core.internal;

import java.lang.reflect.Array;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Default deterministic canonicalizer for parameter values.
 */
public final class DefaultParameterCanonicalizer {

    private DefaultParameterCanonicalizer() {
    }

    public static String canonicalize(Object value) {
        return canonicalize(value, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static String canonicalize(Object value, Set<Object> visiting) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof Class<?> c) {
            return c.getName();
        }
        if (value instanceof Enum<?> e) {
            return e.name();
        }
        if (value instanceof Optional<?> o) {
            return o.map(v -> canonicalize(v, visiting)).orElse("empty");
        }
        if (value instanceof TemporalAccessor t) {
            return t.toString();
        }
        if (value.getClass().isArray()) {
            return canonicalizeArray(value, visiting);
        }
        if (value instanceof Iterable<?> iterable) {
            return canonicalizeIterable(iterable, visiting);
        }
        if (value instanceof Map<?, ?> map) {
            return canonicalizeMap(map, visiting);
        }
        return canonicalizeObject(value);
    }

    private static String canonicalizeArray(Object array, Set<Object> visiting) {
        if (!visiting.add(array)) {
            return cyclic(array);
        }
        try {
            int length = Array.getLength(array);
            List<String> parts = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                parts.add(canonicalize(Array.get(array, i), visiting));
            }
            return "[" + String.join(", ", parts) + "]";
        } finally {
            visiting.remove(array);
        }
    }

    private static String canonicalizeIterable(Iterable<?> iterable, Set<Object> visiting) {
        if (iterable instanceof List<?> list && !visiting.add(list)) {
            return cyclic(list);
        }
        List<String> parts = new ArrayList<>();
        for (Object item : iterable) {
            if (item == iterable) {
                parts.add(cyclic(item));
            } else {
                parts.add(canonicalize(item, visiting));
            }
        }
        return "[" + String.join(", ", parts) + "]";
    }

    private static String canonicalizeMap(Map<?, ?> map, Set<Object> visiting) {
        if (!visiting.add(map)) {
            return cyclic(map);
        }
        try {
            List<String> keys = new ArrayList<>();
            for (Object key : map.keySet()) {
                keys.add(canonicalize(key, visiting));
            }
            Collections.sort(keys);
            List<String> entries = new ArrayList<>(keys.size());
            for (String key : keys) {
                Object rawKey = findRawKey(map, key, visiting);
                Object rawValue = map.get(rawKey);
                entries.add(key + "=" + canonicalize(rawValue, visiting));
            }
            return "{" + String.join(", ", entries) + "}";
        } finally {
            visiting.remove(map);
        }
    }

    private static Object findRawKey(Map<?, ?> map, String canonicalKey, Set<Object> visiting) {
        for (Object key : map.keySet()) {
            if (canonicalize(key, visiting).equals(canonicalKey)) {
                return key;
            }
        }
        throw new IllegalStateException("Canonical key not found: " + canonicalKey);
    }

    private static String canonicalizeObject(Object value) {
        String str = value.toString();
        String className = value.getClass().getName();
        if (looksLikeIdentityToString(str, className)) {
            return className;
        }
        return str;
    }

    private static boolean looksLikeIdentityToString(String str, String className) {
        String simpleName = className;
        int lastDot = className.lastIndexOf('.');
        if (lastDot >= 0) {
            simpleName = className.substring(lastDot + 1);
        }
        String prefix = simpleName + "@";
        if (!str.startsWith(prefix)) {
            return false;
        }
        String suffix = str.substring(prefix.length());
        return suffix.matches("^[0-9a-fA-F]+$");
    }

    private static String cyclic(Object value) {
        return "[cyclic:" + value.getClass().getName() + "]";
    }
}
