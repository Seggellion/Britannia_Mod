package com.seggellion.britannia_mod.vegetation;

import com.seggellion.britannia_mod.farming.FlowerDefinition;
import com.seggellion.britannia_mod.farming.FlowerRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Adapter over the existing authoritative flower registry; it owns no species list. */
public final class ManagedFlowerSpecies {
    private ManagedFlowerSpecies() {
    }

    public static List<ResourceLocation> available() {
        return FlowerRegistry.initial().definitions().keySet().stream()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
    }

    public static Optional<FlowerDefinition> definition(ResourceLocation speciesId) {
        return FlowerRegistry.initial().byId(speciesId);
    }

    public static ResourceLocation select(RandomSource random) {
        Objects.requireNonNull(random, "Flower species random source is required");
        List<ResourceLocation> species = available();
        if (species.isEmpty()) {
            throw new IllegalStateException("The authoritative flower registry contains no species");
        }
        return species.get(random.nextInt(species.size()));
    }
}
