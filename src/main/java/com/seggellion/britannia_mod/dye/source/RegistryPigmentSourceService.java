package com.seggellion.britannia_mod.dye.source;

import com.seggellion.britannia_mod.bannerdyeing.registry.DefinitionEntry;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.data.PigmentDefinition;
import com.seggellion.britannia_mod.dye.item.PigmentItem;
import com.seggellion.britannia_mod.registry.DyeItemRegistry;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/** Server-owned, acquisition-neutral pigment stack creation with no inventory or client behavior. */
public final class RegistryPigmentSourceService implements PigmentSourceService {
    private final PigmentItemResolver itemResolver;

    public RegistryPigmentSourceService() {
        this(DyeItemRegistry::pigmentItem);
    }

    public RegistryPigmentSourceService(PigmentItemResolver itemResolver) {
        this.itemResolver = Objects.requireNonNull(itemResolver, "itemResolver");
    }

    @Override
    public PigmentSourceResult createPigmentStack(
            PigmentId pigmentId, int count, RegistrySnapshot snapshot, boolean registryAvailable) {
        Objects.requireNonNull(pigmentId, "pigmentId");
        Objects.requireNonNull(snapshot, "snapshot");
        if (!registryAvailable) {
            return PigmentSourceResult.failure(PigmentSourceFailure.REGISTRY_UNAVAILABLE);
        }
        if (!snapshot.pigments().contains(pigmentId)) {
            boolean disabled = snapshot.pigments().disabledEntries().stream()
                    .map(DefinitionEntry::id).anyMatch(pigmentId::equals);
            return PigmentSourceResult.failure(disabled
                    ? PigmentSourceFailure.PIGMENT_DISABLED : PigmentSourceFailure.PIGMENT_MISSING);
        }
        PigmentItem item = itemResolver.resolve(pigmentId).orElse(null);
        if (item == null || !pigmentId.equals(item.pigmentId())) {
            return PigmentSourceResult.failure(PigmentSourceFailure.ITEM_MAPPING_MISSING);
        }
        if (count <= 0) {
            return PigmentSourceResult.failure(PigmentSourceFailure.INVALID_COUNT);
        }
        ItemStack probe = new ItemStack(item);
        if (count > probe.getMaxStackSize()) {
            return PigmentSourceResult.failure(PigmentSourceFailure.COUNT_EXCEEDS_STACK_LIMIT);
        }
        return PigmentSourceResult.success(new ItemStack(item, count));
    }

    @Override
    public PigmentSourceListResult listAvailablePigments(
            RegistrySnapshot snapshot, boolean registryAvailable) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (!registryAvailable) {
            return new PigmentSourceListResult(List.of(), PigmentSourceFailure.REGISTRY_UNAVAILABLE);
        }
        List<PigmentSourceEntry> entries = snapshot.pigments().activeDefinitions().stream()
                .map(definition -> entry(definition, itemResolver.resolve(definition.id()).orElse(null)))
                .filter(Objects::nonNull)
                .sorted(java.util.Comparator.comparing(entry -> entry.pigmentId().toString()))
                .toList();
        return new PigmentSourceListResult(entries, PigmentSourceFailure.NONE);
    }

    private static PigmentSourceEntry entry(PigmentDefinition definition, PigmentItem item) {
        if (item == null || !definition.id().equals(item.pigmentId())) {
            return null;
        }
        return new PigmentSourceEntry(definition.id(), definition.displayNameKey(),
                BuiltInRegistries.ITEM.getKey(item), definition.rarity(), definition.tags());
    }
}
