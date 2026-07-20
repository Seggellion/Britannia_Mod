package com.seggellion.britannia_mod.bannerdyeing.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/** Immutable, deterministically ordered active and disabled views for one definition domain. */
public final class DefinitionRegistry<I, T> {
    private final Map<I, DefinitionEntry<I, T>> activeEntries;
    private final List<DefinitionEntry<I, T>> disabledEntries;
    private final List<T> activeDefinitions;

    DefinitionRegistry(
            Map<I, DefinitionEntry<I, T>> activeEntries,
            List<DefinitionEntry<I, T>> disabledEntries) {
        List<DefinitionEntry<I, T>> sortedActive = new ArrayList<>(activeEntries.values());
        sortedActive.sort(Comparator.comparing(entry -> entry.id().toString()));
        Map<I, DefinitionEntry<I, T>> ordered = new LinkedHashMap<>();
        sortedActive.forEach(entry -> ordered.put(entry.id(), entry));
        this.activeEntries = Collections.unmodifiableMap(ordered);

        List<DefinitionEntry<I, T>> sortedDisabled = new ArrayList<>(disabledEntries);
        sortedDisabled.sort(Comparator
                .comparing((DefinitionEntry<I, T> entry) -> entry.id().toString())
                .thenComparing(entry -> entry.sourceResource().toString()));
        this.disabledEntries = List.copyOf(sortedDisabled);
        this.activeDefinitions = sortedActive.stream().map(DefinitionEntry::definition).toList();
    }

    public Optional<T> find(I id) {
        DefinitionEntry<I, T> entry = activeEntries.get(id);
        return entry == null ? Optional.empty() : Optional.of(entry.definition());
    }

    public T require(I id) {
        return find(id).orElseThrow(() -> new NoSuchElementException("No active definition for stable ID " + id));
    }

    public boolean contains(I id) {
        return activeEntries.containsKey(id);
    }

    public List<T> activeDefinitions() {
        return activeDefinitions;
    }

    public Map<I, DefinitionEntry<I, T>> activeEntries() {
        return activeEntries;
    }

    public List<DefinitionEntry<I, T>> disabledEntries() {
        return disabledEntries;
    }

    public int activeCount() {
        return activeEntries.size();
    }

    public int disabledCount() {
        return disabledEntries.size();
    }
}
