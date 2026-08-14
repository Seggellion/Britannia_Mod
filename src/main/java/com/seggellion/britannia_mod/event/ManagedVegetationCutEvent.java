package com.seggellion.britannia_mod.event;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;

import java.util.Objects;
import java.util.Optional;

/** Successful logical cut notification for future jobs/services; it does not control the cut. */
public final class ManagedVegetationCutEvent extends Event {
    private final ServerPlayer player;
    private final ResourceKey<Level> dimension;
    private final BlockPos nodePosition;
    private final ResourceLocation vegetationEntryId;
    private final Optional<ResourceLocation> flowerSpeciesId;
    private final int flowerStage;

    public ManagedVegetationCutEvent(
            ServerPlayer player,
            ResourceKey<Level> dimension,
            BlockPos nodePosition,
            ResourceLocation vegetationEntryId,
            Optional<ResourceLocation> flowerSpeciesId,
            int flowerStage
    ) {
        this.player = Objects.requireNonNull(player, "Cut player is required");
        this.dimension = Objects.requireNonNull(dimension, "Cut dimension is required");
        this.nodePosition = Objects.requireNonNull(nodePosition, "Cut node position is required").immutable();
        this.vegetationEntryId = Objects.requireNonNull(vegetationEntryId, "Cut vegetation entry is required");
        this.flowerSpeciesId = Objects.requireNonNull(flowerSpeciesId, "Flower species optional is required");
        if (flowerStage < 0 || flowerStage > 7
                || (flowerSpeciesId.isPresent() && flowerStage == 0)
                || (flowerSpeciesId.isEmpty() && flowerStage != 0)) {
            throw new IllegalArgumentException("Cut flower stage must match optional flower identity");
        }
        this.flowerStage = flowerStage;
    }

    public ServerPlayer player() {
        return player;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public BlockPos nodePosition() {
        return nodePosition;
    }

    public ResourceLocation vegetationEntryId() {
        return vegetationEntryId;
    }

    public Optional<ResourceLocation> flowerSpeciesId() {
        return flowerSpeciesId;
    }

    public int flowerStage() {
        return flowerStage;
    }
}
