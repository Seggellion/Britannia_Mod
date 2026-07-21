package com.seggellion.britannia_mod.client.banner;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Small access-ordered cache used only for immutable client render entries. */
public final class BannerRenderEntryCache<V> {
    private final int maximumSize;
    private final Map<BannerRenderKey, V> entries;

    public BannerRenderEntryCache(int maximumSize) {
        if (maximumSize < 1) {
            throw new IllegalArgumentException("maximumSize must be positive");
        }
        this.maximumSize = maximumSize;
        this.entries = new LinkedHashMap<>(32, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<BannerRenderKey, V> eldest) {
                return size() > BannerRenderEntryCache.this.maximumSize;
            }
        };
    }

    public synchronized V getOrCreate(BannerRenderKey key, Function<BannerRenderKey, V> factory) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(factory, "factory");
        V existing = entries.get(key);
        if (existing != null) {
            return existing;
        }
        V created = Objects.requireNonNull(factory.apply(key), "created render entry");
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
