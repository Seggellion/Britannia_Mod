package com.seggellion.britannia_mod.farming;

import net.minecraft.util.RandomSource;

import java.util.Objects;

/** Version 1 selector: species weights only, with context retained for future planting-time bias. */
public final class WeightedFlowerColorSelector implements FlowerColorSelector {
    private final FlowerRegistry registry;

    public WeightedFlowerColorSelector(FlowerRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "Flower registry is required");
    }

    @Override
    public FlowerColor select(
            FlowerDefinition species,
            FlowerPlantingContext context,
            RandomSource random,
            FlowerColorSelectionReason reason
    ) {
        Objects.requireNonNull(species, "Flower species is required");
        Objects.requireNonNull(context, "Flower planting context is required");
        Objects.requireNonNull(random, "Injected flower random source is required");
        if (reason != FlowerColorSelectionReason.SUCCESSFUL_SERVER_PLANTING) {
            throw new IllegalStateException("Flower color selection is permitted only during a successful server planting transaction, not " + reason);
        }
        if (!context.logicalServer()) {
            throw new IllegalStateException("Flower color selection cannot run on the logical client");
        }
        FlowerDefinition registered = registry.byId(species.id())
                .orElseThrow(() -> new IllegalArgumentException("Unregistered flower species: " + species.id()));
        if (registered != species && !registered.equals(species)) {
            throw new IllegalArgumentException("Flower species does not match the registered definition: " + species.id());
        }

        int totalWeight = 0;
        for (FlowerPaletteEntry entry : species.palette()) {
            totalWeight = Math.addExact(totalWeight, entry.weight());
        }
        int roll = random.nextInt(totalWeight);
        for (FlowerPaletteEntry entry : species.palette()) {
            roll -= entry.weight();
            if (roll < 0) {
                return registry.color(entry.colorId())
                        .orElseThrow(() -> new IllegalStateException("Validated flower color disappeared: " + entry.colorId()))
                        .color();
            }
        }
        throw new IllegalStateException("Flower weighted selection exhausted a validated palette for " + species.id());
    }
}
