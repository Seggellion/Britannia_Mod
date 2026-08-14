package com.seggellion.britannia_mod.wildresource;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Small deterministic registry used by the generic scheduler and resource-specific modules. */
public final class WildResourceRegistry {
    private final Map<ResourceLocation, WildResourceEntry> entries = new LinkedHashMap<>();

    public WildResourceEntry register(WildResourceEntry entry) {
        Objects.requireNonNull(entry, "entry");
        if (entries.putIfAbsent(entry.id(), entry) != null) {
            throw new IllegalArgumentException("Duplicate wild resource id " + entry.id());
        }
        return entry;
    }

    public Optional<WildResourceEntry> find(ResourceLocation id) {
        return Optional.ofNullable(entries.get(id));
    }

    public Collection<WildResourceEntry> entries() {
        return List.copyOf(entries.values());
    }

    public Optional<WildResourceEntry> select(RandomSource random) {
        return select(random, entries.values());
    }

    public Optional<WildResourceEntry> select(RandomSource random, Collection<WildResourceEntry> candidates) {
        List<WildResourceEntry> selectable = List.copyOf(candidates);
        if (selectable.isEmpty()) {
            return Optional.empty();
        }
        int totalWeight = selectable.stream().mapToInt(WildResourceEntry::spawnWeight).sum();
        int selected = random.nextInt(totalWeight);
        for (WildResourceEntry entry : selectable) {
            selected -= entry.spawnWeight();
            if (selected < 0) {
                return Optional.of(entry);
            }
        }
        throw new IllegalStateException("Wild resource weights did not resolve");
    }
}
