package com.seggellion.britannia_mod.vegetation;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

/** Immutable persistent state for one managed vegetation location. */
public record ManagedVegetationNode(
        BlockPos position,
        ManagedVegetationLifecycle lifecycle,
        Optional<ResourceLocation> vegetationEntryId,
        Optional<ResourceLocation> flowerSpeciesId,
        int flowerStage,
        long nextTransitionGameTime
) {
    public static final long NO_TRANSITION = -1L;

    public ManagedVegetationNode {
        position = Objects.requireNonNull(position, "Managed vegetation position is required").immutable();
        lifecycle = Objects.requireNonNull(lifecycle, "Managed vegetation lifecycle is required");
        vegetationEntryId = Objects.requireNonNull(vegetationEntryId, "Vegetation entry optional is required");
        flowerSpeciesId = Objects.requireNonNull(flowerSpeciesId, "Flower species optional is required");
        if (nextTransitionGameTime < NO_TRANSITION) {
            throw new IllegalArgumentException("Transition time must be -1 or non-negative");
        }
        if (lifecycle.occupied() && vegetationEntryId.isEmpty()) {
            throw new IllegalArgumentException("Occupied vegetation requires an entry ID");
        }
        if (lifecycle == ManagedVegetationLifecycle.FLOWER) {
            if (flowerSpeciesId.isEmpty() || flowerStage < 1 || flowerStage > 7) {
                throw new IllegalArgumentException("Managed flowers require a species and stage in 1..7");
            }
        } else if (flowerSpeciesId.isPresent() || flowerStage != 0) {
            throw new IllegalArgumentException("Non-flower vegetation cannot carry flower state");
        }
    }

    public static ManagedVegetationNode regrowing(BlockPos position, long nextTransitionGameTime) {
        return new ManagedVegetationNode(
                position,
                ManagedVegetationLifecycle.REGROWING,
                Optional.empty(),
                Optional.empty(),
                0,
                nextTransitionGameTime
        );
    }

    public ManagedVegetationNode shortGrass(ResourceLocation entryId, long transitionTime) {
        return occupied(ManagedVegetationLifecycle.SHORT_GRASS, entryId, transitionTime);
    }

    public ManagedVegetationNode tallGrass(ResourceLocation entryId) {
        return occupied(ManagedVegetationLifecycle.TALL_GRASS, entryId, NO_TRANSITION);
    }

    public ManagedVegetationNode fern(ResourceLocation entryId) {
        return occupied(ManagedVegetationLifecycle.FERN, entryId, NO_TRANSITION);
    }

    public ManagedVegetationNode flower(
            ResourceLocation entryId,
            ResourceLocation speciesId,
            int stage,
            long transitionTime
    ) {
        return new ManagedVegetationNode(
                position,
                ManagedVegetationLifecycle.FLOWER,
                Optional.of(Objects.requireNonNull(entryId, "Vegetation entry ID is required")),
                Optional.of(Objects.requireNonNull(speciesId, "Flower species ID is required")),
                stage,
                transitionTime
        );
    }

    public ManagedVegetationNode schedule(long transitionTime) {
        return new ManagedVegetationNode(
                position, lifecycle, vegetationEntryId, flowerSpeciesId, flowerStage, transitionTime
        );
    }

    public ManagedVegetationNode beginRegrowth(long transitionTime) {
        return regrowing(position, transitionTime);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Position", position.asLong());
        tag.putString("Lifecycle", lifecycle.name());
        vegetationEntryId.ifPresent(id -> tag.putString("EntryId", id.toString()));
        flowerSpeciesId.ifPresent(id -> tag.putString("FlowerSpeciesId", id.toString()));
        tag.putInt("FlowerStage", flowerStage);
        tag.putLong("NextTransitionGameTime", nextTransitionGameTime);
        return tag;
    }

    public static ManagedVegetationNode fromTag(CompoundTag tag) {
        if (!tag.contains("Position") || !tag.contains("Lifecycle")
                || !tag.contains("NextTransitionGameTime")) {
            throw new IllegalArgumentException("Managed vegetation node is missing required state");
        }
        ManagedVegetationLifecycle lifecycle;
        try {
            lifecycle = ManagedVegetationLifecycle.valueOf(tag.getString("Lifecycle"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown managed vegetation lifecycle: "
                    + tag.getString("Lifecycle"), exception);
        }
        return new ManagedVegetationNode(
                BlockPos.of(tag.getLong("Position")),
                lifecycle,
                optionalId(tag, "EntryId"),
                optionalId(tag, "FlowerSpeciesId"),
                tag.getInt("FlowerStage"),
                tag.getLong("NextTransitionGameTime")
        );
    }

    private ManagedVegetationNode occupied(
            ManagedVegetationLifecycle nextLifecycle,
            ResourceLocation entryId,
            long transitionTime
    ) {
        return new ManagedVegetationNode(
                position,
                nextLifecycle,
                Optional.of(Objects.requireNonNull(entryId, "Vegetation entry ID is required")),
                Optional.empty(),
                0,
                transitionTime
        );
    }

    private static Optional<ResourceLocation> optionalId(CompoundTag tag, String key) {
        if (!tag.contains(key)) {
            return Optional.empty();
        }
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(key));
        if (id == null) {
            throw new IllegalArgumentException("Invalid resource location in " + key + ": " + tag.getString(key));
        }
        return Optional.of(id);
    }
}
