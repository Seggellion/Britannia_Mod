package com.seggellion.britannia_mod.client.banner;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Access-ordered bounded cache for immutable shared appearance products. */
public final class BannerAppearanceEntryCache<V> {
    private final int maximumSize;
    private final Map<BannerAppearanceKey, V> entries;

    public BannerAppearanceEntryCache(int maximumSize) {
        if (maximumSize < 1) {
            throw new IllegalArgumentException("maximumSize must be positive");
        }
        this.maximumSize = maximumSize;
        this.entries = new LinkedHashMap<>(32, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<BannerAppearanceKey, V> eldest) {
                return size() > BannerAppearanceEntryCache.this.maximumSize;
            }
        };
    }

    public synchronized V getOrCreate(
            BannerAppearanceKey key, Function<BannerAppearanceKey, V> factory) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(factory, "factory");
        V existing = entries.get(key);
        if (existing != null) {
            return existing;
        }
        V created = Objects.requireNonNull(factory.apply(key), "created appearance entry");
        entries.put(key, created);
        return created;
    }

    public synchronized void clear() {
        entries.clear();
    }

    public synchronized int size() {
        return entries.size();
    }
}
