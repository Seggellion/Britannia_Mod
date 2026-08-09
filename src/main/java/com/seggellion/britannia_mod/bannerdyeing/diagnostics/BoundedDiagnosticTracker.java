package com.seggellion.britannia_mod.bannerdyeing.diagnostics;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Thread-safe insertion-ordered diagnostic de-duplication with a strict maximum cardinality. */
public final class BoundedDiagnosticTracker<K> {
    private final int maximumSize;
    private final Set<K> keys = new LinkedHashSet<>();

    public BoundedDiagnosticTracker(int maximumSize) {
        if (maximumSize < 1) {
            throw new IllegalArgumentException("maximumSize must be positive");
        }
        this.maximumSize = maximumSize;
    }

    public synchronized boolean first(K key) {
        Objects.requireNonNull(key, "key");
        if (keys.contains(key)) {
            return false;
        }
        if (keys.size() >= maximumSize) {
            var oldest = keys.iterator();
            if (oldest.hasNext()) {
                oldest.next();
                oldest.remove();
            }
        }
        return keys.add(key);
    }

    public synchronized void clear() {
        keys.clear();
    }

    public synchronized int size() {
        return keys.size();
    }

    public int maximumSize() {
        return maximumSize;
    }
}
