package com.seggellion.britannia_mod.client.banner;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Bounded access-ordered placed-context cache with no position or world ownership. */
public final class BannerPlacedRenderEntryCache<V> {
    private final int maximumSize;
    private final Map<BannerPlacedRenderKey, V> entries;

    public BannerPlacedRenderEntryCache(int maximumSize) {
        if (maximumSize < 1) {
            throw new IllegalArgumentException("maximumSize must be positive");
        }
        this.maximumSize = maximumSize;
        this.entries = new LinkedHashMap<>(32, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<BannerPlacedRenderKey, V> eldest) {
                return size() > BannerPlacedRenderEntryCache.this.maximumSize;
            }
        };
    }

    public synchronized V getOrCreate(
            BannerPlacedRenderKey key, Function<BannerPlacedRenderKey, V> factory) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(factory, "factory");
        V existing = entries.get(key);
        if (existing != null) {
            return existing;
        }
        V created = Objects.requireNonNull(factory.apply(key), "created placed render entry");
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
